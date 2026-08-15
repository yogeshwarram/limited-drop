package com.limiteddrop.application;
public class HoldExpiredException extends ConflictException { public HoldExpiredException() { super("The hold has expired"); } }
