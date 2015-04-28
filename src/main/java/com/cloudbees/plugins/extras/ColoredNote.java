package com.cloudbees.plugins.extras;

import hudson.MarkupText;
import hudson.console.ConsoleAnnotator;
import hudson.console.ConsoleNote;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ColoredNote extends ConsoleNote {

    private final String color;

    public ColoredNote(String color) {
        this.color = color;
    }

    @Override
    public ConsoleAnnotator annotate(Object context, MarkupText text, int charPos) {
        try {
            text.addMarkup(charPos, charPos + text.length(), "<span style='color:"+color+"'>", "</span>");
        } catch(Exception e) {
            LOGGER.log(Level.WARNING, "Failed to annotate: " + text.getText() + ". pos: " + charPos + ", text len: " + text.length() + ", ", e);
        }
        return null;
    }

    public static String redNote(String text) {
        try {
            return new ColoredNote("#ce4844").encode() + text;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to serialize " + ColoredNote.class,e);
            return text;
        }
    }

    public static String blueNote(String text) {
        try {
            return new ColoredNote("#1b809e").encode() + text;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to serialize " + ColoredNote.class,e);
            return text;
        }
    }

    public static String greenNote(String text) {
        try {
            return new ColoredNote("#5cb85c").encode() + text;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to serialize " + ColoredNote.class,e);
            return text;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ColoredNote.class.getName());
}
