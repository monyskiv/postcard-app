package com.markoonyskiv.postcards.service;

/**
 * Thrown when an upload is a well-formed image that this service can't
 * process within the heap it has - as opposed to {@link InvalidImageException},
 * which means the upload itself was wrong (empty, unsupported type,
 * undecodable). Kept separate so the client gets 413 and a message that
 * tells them what to do about it (upload something smaller) instead of a
 * bare 500.
 */
public class ImageTooLargeException extends RuntimeException {

    public ImageTooLargeException(String message) {
        super(message);
    }
}
