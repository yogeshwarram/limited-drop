package com.limiteddrop.application;
public class DropNotOpenException extends RuntimeException { public DropNotOpenException() { super("The drop is not open yet"); } }
