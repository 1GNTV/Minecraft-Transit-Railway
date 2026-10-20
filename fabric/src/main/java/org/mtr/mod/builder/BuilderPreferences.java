package org.mtr.mod.builder;

import java.nio.file.*;
import java.io.*;
import java.util.Properties;

/** Client preferences only. No changes to MTR save schemas or resource identifiers. */
public final class BuilderPreferences {
    private static final Path FILE=Path.of("config","sillon-builder.properties");
    private static final Properties VALUES=new Properties();
    static {try(var in=Files.newInputStream(FILE)){VALUES.load(in);}catch(IOException ignored){}}
    private BuilderPreferences(){}
    public static boolean enabled(String key){return Boolean.parseBoolean(VALUES.getProperty(key,"true"));}
    public static void set(String key,boolean value){VALUES.setProperty(key,Boolean.toString(value));try{Files.createDirectories(FILE.getParent());try(var out=Files.newOutputStream(FILE)){VALUES.store(out,"Sillon builder interfaces. Set an option to false to keep the original MTR interface.");}}catch(IOException ignored){}}
}
