package com.nanthno;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class CsvExporting {

    static final String topLine = "Name;Email;Company";


    static void saveEmailDataToCsv(EmailData[] emails, String fileName) {
        saveEmailDataToCsv(emails, fileName, 0);
    }

    static void saveEmailDataToCsv(EmailData[] emails, String fileName, int fileNameCount) {
        System.out.println(fileName);

        String name;
        if (fileNameCount == 0)
            name = fileName + ".csv";
        else
            name = String.format("%s%d.csv", fileName, fileNameCount);
        try {
                FileWriter writer = new FileWriter(name);
            BufferedWriter bufferedWriter = new BufferedWriter(writer);

            bufferedWriter.write(topLine);
            bufferedWriter.newLine();
            for (EmailData data : emails) {
                bufferedWriter.write(data.toString());
                bufferedWriter.newLine();
            }
            bufferedWriter.close();
            writer.close();

        } catch (IOException e) {
            e.printStackTrace();
            saveEmailDataToCsv(emails, fileName, fileNameCount++);
        }

        System.out.println(String.format("Saving data to %s", name));
    }
}
