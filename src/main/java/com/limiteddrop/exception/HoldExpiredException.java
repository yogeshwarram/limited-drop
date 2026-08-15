package com.limiteddrop.exception;
public class HoldExpiredException extends ConflictException { public HoldExpiredException() { super("The hold has expired"); } }
