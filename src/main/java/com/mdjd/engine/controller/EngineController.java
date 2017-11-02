package com.mdjd.engine.controller;


import com.mdjd.engine.entities.Engine;
import com.mdjd.engine.repository.EngineRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EngineController {

    @Autowired
    private EngineRepository repository;

    @RequestMapping(value = "/create", method = RequestMethod.GET)
    public int create(@RequestParam("firstPlayer") String firstPlayer, @RequestParam("secondPlayer") String secondPlayer) {
        return repository.create(firstPlayer, secondPlayer);
    }


    @RequestMapping(value = "/processing", method = RequestMethod.POST)
    public Engine move(@RequestParam("gameId") int gameId, @RequestParam("player") int player, @RequestParam("coordinate") int[][] coordinate) {
        return repository.move(gameId, player, coordinate);
    }


    @RequestMapping(value = "/quit", method = RequestMethod.POST)
    public Engine gameFinished(@RequestParam("gameId") int gameId, @RequestParam(value = "player", defaultValue = "0") int player) {
        return repository.gameFinished(gameId, player);
    }

}