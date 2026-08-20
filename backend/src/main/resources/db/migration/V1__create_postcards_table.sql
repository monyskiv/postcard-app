CREATE TABLE postcards (
    id               UUID PRIMARY KEY,
    title            VARCHAR(255) NOT NULL,
    year             INTEGER,
    author           VARCHAR(255) NOT NULL,
    description      TEXT NOT NULL,
    color            VARCHAR(20) NOT NULL CHECK (color IN ('COLOR', 'BLACK_AND_WHITE')),
    location         VARCHAR(255) NOT NULL,
    front_image_url  VARCHAR(2048) NOT NULL,
    back_image_url   VARCHAR(2048) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
