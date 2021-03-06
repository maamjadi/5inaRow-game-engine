package com.mdjd.engine.repository;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mdjd.engine.entities.Engine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

@Repository
public class EngineRepository {

    @Autowired
    private MongoTemplate mongoTemplate;

    private int currentPlayer;
    private int[][] board;
    private String currentGameId;

    private enum Msg {
        DATABASE(0, "A database error has occured"),
        UNTURN_MOVE(1, "This user already moved"),
        EMPTY_SQUARE(2, "This is not an empty square"),
        START_NEW_GAME(3, "Finish the current game first"),
        NO_GAME_FOUND(4, "This Game Id is invalid"),
        PLAYER_RESIGNS(5, "Player resigns, you have won"),
        DRAW(6, "The game ends in a draw"),
        GAME_FINISHED(7, "The game is finished"),
        PLAYER_MOVED(8, "The player has moved"),
        NOT_YOUR_TURN(9, "It is not your turn"),
        YOUR_TURN(10, "It is your turn");


        private final int code;
        private final String description;

        private Msg(int code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        public int getCode() {
            return code;
        }

        @Override
        public String toString() {
            return code + ": " + description;
        }

        public ObjectNode toJson(boolean showCode) {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode value;
            if (showCode == true) {
                value = mapper.createObjectNode().put("code", code);
            } else {
                value = mapper.createObjectNode().put("Message", description);
            }
            return value;
        }
    }

    public ResponseEntity create(String firstPlayer, String secondPlayer) {
//        int gameId = getNewGameId();
        Engine engine = new Engine(firstPlayer, secondPlayer);
        mongoTemplate.save(engine, "engine");
        System.out.print(engine);
        String responseValue = new ObjectMapper().createObjectNode().put("game_iD",engine.getGameID()).toString();
        return ResponseEntity.status(HttpStatus.CREATED).body(responseValue);
    }

    private ResponseEntity calculateScore(String player, int moves) throws IOException {
        String urlString = String.format("http://localhost:8080/5inarow/score?player=%s&moves=%2d", player, moves);
        URL url = new URL(urlString);
        HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
        httpCon.setRequestMethod("PUT");
        return ResponseEntity.status(httpCon.getResponseCode()).body(httpCon.getResponseMessage());
    }

    private Engine retreiveEngine(String gameId) {
        Criteria c = Criteria.where("_id").is(gameId);
        Query query = new Query().addCriteria(c);
        Engine engine = mongoTemplate.findOne(query, Engine.class);
        return engine;
    }

    private int updateEngine(Update update) {
        mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(currentGameId)), update, "engine");
        Engine engine =mongoTemplate.findOne(new Query(Criteria.where("_id").is(currentGameId)), Engine.class);
        System.out.print(engine);
        return Msg.DATABASE.getCode();
    }

    public ResponseEntity gameState(String gameId, int player) {
        Engine engine = retreiveEngine(gameId);
        board = engine.getCoordinatePlane();
        String whoMadeLastMove = getPlayerUsername(engine, engine.getLastPlayer());
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode commonJsonData = mapper.createObjectNode().put("who_made_last_move",whoMadeLastMove)
                .put("row",engine.getRow())
                .put("column",engine.getColumn());

        boolean emptySpace = checkBoardIsFull();
        if (emptySpace == false) {
            return ResponseEntity.status(HttpStatus.RESET_CONTENT).body(commonJsonData.put("code", Msg.DRAW.getCode()).toString());
        }

        String msgCode;

        if (player == engine.getLastPlayer()) {
            msgCode = commonJsonData.put("code", Msg.NOT_YOUR_TURN.getCode()).toString();
        } else {
            msgCode = commonJsonData.put("code", Msg.YOUR_TURN.getCode()).toString();
        }

        if (engine.getWinner() != 0) {
            return ResponseEntity.status(HttpStatus.RESET_CONTENT).body(commonJsonData.put("code", Msg.GAME_FINISHED.getCode()).toString());
        }

        return ResponseEntity.ok(msgCode);
    }

    public ResponseEntity move(String gameId, int player,int row, int col) {
        Engine engine = retreiveEngine(gameId);
        Update update = new Update();
        //do it here

        board = engine.getCoordinatePlane();
        currentPlayer = player;
        currentGameId = engine.getGameID();

        if (currentPlayer == engine.getLastPlayer()) {
            System.out.print(Msg.UNTURN_MOVE.toJson(false).toString());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Msg.UNTURN_MOVE.toJson(false).toString());
        }

        if ( board[row][col] != 0 ) {
            return ResponseEntity.status(HttpStatus.ALREADY_REPORTED).body(Msg.EMPTY_SQUARE.toJson(false).toString());
        }

        board[row][col] = player; //make the move
        update.set("row", row);
        update.set("column", col);
        update.set("coordinatePlane",board);
        update.set("lastPlayer",currentPlayer);

        if (winner(row,col)) {  // First, check for a winner.
            if (currentPlayer == 1) {
                update.set("winner", 1);
            } else {
                update.set("winner", 2);
            }
            return ResponseEntity.status(HttpStatus.RESET_CONTENT).body(Msg.GAME_FINISHED.toJson(false).toString());
        }

        boolean emptySpace = checkBoardIsFull();
        if (emptySpace == false) {
            return ResponseEntity.status(HttpStatus.RESET_CONTENT).body(Msg.DRAW.toJson(false).toString());
        }

        updateEngine(update);
        String whoesTurnItIs = getPlayerUsername(engine, currentPlayer);
        return ResponseEntity.ok((Msg.PLAYER_MOVED.toJson(false).put("who_made_last_move",whoesTurnItIs)).toString());
    }

    private String getPlayerUsername(Engine engine, int player) {
        String playerUsername;
        if (player == 1) {
            playerUsername = engine.getFirstPlayerUsername();
        } else {
            playerUsername = engine.getSecondPlayerUsername();
        }
        return playerUsername;
    }

    private boolean checkBoardIsFull() {
        boolean emptySpace = false;     // Check if the board is full.
        for (int i = 0; i < 18; i++)
            for (int j = 0; j < 18; j++)
                if (board[i][j] == 0)
                    emptySpace = true;
        return emptySpace;
    }

    private boolean winner(int row, int col) {
        //horizontal direction: 1 0
        //vertical direction: 0 1
        //first diagonal: 1 1
        //second diagonal: 1 -1
        //should check for >= 5 because it is possible that player place a piece in an empty square that joins to other short rows of pieces

        if (count(currentPlayer, row, col, 1, 0 ) >= 5)
            return true;
        if (count(currentPlayer, row, col, 0, 1 ) >= 5)
            return true;
        if (count(currentPlayer, row, col, 1, -1 ) >= 5)
            return true;
        if (count(currentPlayer, row, col, 1, 1 ) >= 5)
            return true;

          /* When we get to this point, we know that the game is not won. */

        return false;

    }

    private int count(int player, int row, int col, int dirX, int dirY) {

        int ct = 1;  // Number of pieces in a row belonging to the player.

        int r, c;    // A row and column to be examined

        r = row + dirX;  // Look at square in specified direction.
        c = col + dirY;
        while ( r >= 0 && r < 18 && c >= 0 && c < 18 && board[r][c] == player ) {
            // Square is on the board and contains one of the players's pieces.
            ct++;
            r += dirX;  // Go on to next square in this direction.
            c += dirY;
        }

        //    did not contain one of the player's pieces.

        r = row - dirX;  // Look in the opposite direction.
        c = col - dirY;
        while ( r >= 0 && r < 18 && c >= 0 && c < 18 && board[r][c] == player ) {
            // Square is on the board and contains one of the players's pieces.
            ct++;
            r -= dirX;   // Go on to next square in this direction.
            c -= dirY;
        }

        return ct;
    }

    private int calculateTheMoves(int player) {
        int moves = 0;
        for (int i = 0; i < 18; i++)
            for (int j = 0; j < 18; j++)
                if (board[i][j] == player)
                    moves++;
        return moves;
    }


    public ResponseEntity gameFinished(String gameId, int player) {
        Engine engine = retreiveEngine(gameId);
        String username;
        if (player == 1) {
            username = engine.getFirstPlayerUsername();
        } else {
            username = engine.getSecondPlayerUsername();
        }
        try {
            ResponseEntity response = calculateScore(username, calculateTheMoves(player));
            removeTheGameDB(gameId);
            return response;
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e);
        }
    }

    private void removeTheGameDB(String gameId) {
        Engine engine = mongoTemplate.findById(gameId, Engine.class, "engine");
        mongoTemplate.remove(engine);
    }

    @SuppressWarnings("unchecked")
    public List<Integer> findGameIds() {
        return mongoTemplate.getCollection("engine").distinct("_id");
    }


}
