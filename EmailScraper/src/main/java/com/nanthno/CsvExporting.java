package com.nanthno;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;

public class CsvExporting {

    static final String topLine = "Name;Email;Company;Source File;Notes";


    static void saveEmailDataToCsv(EmailData[] emails, String fileName) {
        saveEmailDataToCsv(emails, fileName, 0);
    }

    static void saveEmailDataToCsv(EmailData[] emails, String fileName, int fileNameCount) {
        System.out.println("Packaging data...");

        String name;
        if (fileNameCount == 0)
            name = fileName + ".csv";
        else
            name = String.format("%s%d.csv", fileName, fileNameCount);

        StringBuilder builder = new StringBuilder();
        for (EmailData data : emails) {
            builder.append(data.toString());
            builder.append('\n');
        }

        String outText = builder.toString();
        outText = cleanText(outText);

        File file = new File(name);
        while (file.exists()) {
            fileNameCount++;
            name = String.format("%s%d.csv", fileName, fileNameCount);
            file = new File(name);
        }

        try {
            FileWriter writer = new FileWriter(name);
            BufferedWriter bufferedWriter = new BufferedWriter(writer);

            bufferedWriter.write(topLine);
            bufferedWriter.newLine();
            bufferedWriter.write(outText);
            /*for (EmailData data : emails) {
                bufferedWriter.write(data.toString());
                bufferedWriter.newLine();
            }*/
            bufferedWriter.close();
            writer.close();
            Logging.logPrintInfo(String.format("Saved data to %s", name));

        } catch (IOException e) {
            e.printStackTrace();
            Logging.logPrint(Level.WARNING, "Failed to create output file named " + name);
            saveEmailDataToCsv(emails, fileName, ++fileNameCount);
        }

    }

    static String cleanText(String text) {
        text = StringUtils.stripAccents(text);
        return text;
    }
}
