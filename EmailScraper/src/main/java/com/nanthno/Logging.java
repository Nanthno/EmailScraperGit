package com.nanthno;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Logging {

    static Logger logger = Logger.getLogger(Logging.class.getName());
    static FileHandler fileHandler;

    static void init() {
        try {
            fileHandler = new FileHandler("logs.txt");
        } catch (IOException e) {
            System.out.println("ERROR: Logger failed to start");
            e.printStackTrace();
            System.exit(1);
        }

        logger.setUseParentHandlers(false);
        logger.addHandler(fileHandler);
        logInfo("Logger started");
    }

    static void log(Level level, String message) {
        logger.log(level, message);
    }

    static void logInfo(String message) {
        log(Level.INFO, message);
    }

    static void logPrint(Level level, String message) {
        System.out.println(message);
        log(level, message);
    }

    static void logPrintInfo(String message) {
        System.out.println(message);
        logInfo(message);
    }
    static void logWarning(String message) {
        log(Level.WARNING, message);
    }
}
