package com.markoonyskiv.postcards.controller;

import com.markoonyskiv.postcards.service.PostcardNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PostcardExceptionHandler {

    @ExceptionHandler(PostcardNotFoundException.class)
    public ResponseEntity<Void> handleNotFound(PostcardNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
