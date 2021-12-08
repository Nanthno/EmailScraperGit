package com.nanthno;

import me.tongfei.progressbar.ProgressBar;

import java.awt.image.AffineTransformOp;
import java.io.File;
import java.util.ArrayList;

public class Main {

    static String directoryPath = ".";
    static String outputName = "output";

    public static void main(String[] args) {

        Flags flags = new Flags();

        int numLineFails = 0;
        int numLineSucceed = 0;
        int numLineRepaired = 0;

        if (args.length > 0) {
            directoryPath = args[0];
            if (args.length > 1) {
                outputName = args[1];
            }
        }

        File sourceDirectory = new File(directoryPath);
        if (!sourceDirectory.exists()) {

            System.out.println(String.format("Error: source directory %s does not exist", sourceDirectory.getPath()));

            sourceDirectory = new File(".");
            System.out.println(sourceDirectory.getAbsolutePath());
            System.exit(1);
        }
        File[] files = sourceDirectory.listFiles((dir, name) -> name.endsWith(".png"));
        System.out.println(String.format("Found %d images", files.length));

        // split files into batches
        ArrayList<File>[] fileBatches = new ArrayList[flags.numThreads];
        for (int i = 0; i < flags.numThreads; i++) {
            fileBatches[i] = new ArrayList<File>();
        }
        for (int i = 0; i < files.length; i++) {
            fileBatches[i % flags.numThreads].add(files[i]);
        }

        try (ProgressBar progressBar = new ProgressBar("", files.length)) {

            BatchProcess[] threads = new BatchProcess[flags.numThreads];
            for (int i = 0; i < flags.numThreads; i++) {
                ArrayList<File> batch = fileBatches[i];
                BatchProcess newThread = new BatchProcess(i, batch.toArray(new File[batch.size()]), progressBar, flags);

                if (flags.printLong)
                    System.out.println(String.format("Launching thread %d", i));

                newThread.start();
                threads[i] = newThread;
            }


            try {
                // wait till all threads are done
                for (BatchProcess thread : threads) {
                    thread.join();
                    numLineFails += thread.numLineFails;
                    numLineSucceed += thread.numLineSucceed;
                    numLineRepaired += thread.numLineRepaired;
                }
            } catch (InterruptedException e) {
                System.out.println("Unable to complete. Thread interrupted");
                e.printStackTrace();
                System.exit(0);
            }


            ArrayList<EmailData> allEmails = new ArrayList<>();
            for (BatchProcess batch : threads) {
                allEmails.addAll(batch.getEmails());
            }

            CsvExporting.saveEmailDataToCsv(allEmails.stream().toArray(EmailData[]::new), outputName);
        }
        System.out.println(String.format("Failed to read %d lines", numLineFails));
        System.out.println(String.format("Succeeded reading %d lines", numLineSucceed));
        System.out.println(String.format("Of which %d lines were repaired", numLineRepaired));
    }

}

class BatchProcess extends Thread {

    int threadNum;
    ProgressBar progressBar;

    File[] files;
    ArrayList<EmailData> emails = new ArrayList<>();

    int numLineFails = 0;
    int numLineSucceed = 0;
    int numLineRepaired = 0;

    Flags flags;

    BatchProcess(int threadNum, File[] files, ProgressBar progressBar, Flags flags) {
        this.threadNum = threadNum;
        this.files = files;
        this.progressBar = progressBar;
        this.flags = flags;
    }

    public void run() {

        for (File file : files) {
            if (flags.printLong)
                System.out.println(String.format("Thread %d: Processing %s", threadNum, file.getName()));

            ImageToEmail imageToEmail = new ImageToEmail(flags);
            emails.addAll(imageToEmail.imageToEmailData(file));

            numLineFails += imageToEmail.numLineFails;
            numLineSucceed += imageToEmail.numLineSucceed;
            numLineRepaired += imageToEmail.numLineRepaired;

            progressBar.step();
        }
    }

    public ArrayList<EmailData> getEmails() {
        return emails;
    }
}

class Flags {

    final static int GRAY_SCALE = 1;
    final static int BINERIZE = 2;

    static boolean printLong = false;
    static int numThreads = 8;
    static int color_mode = 1;
    static int binerizeThreshold = 220;
    static int scaleFactor = 1;
    static int affineTransformOp = AffineTransformOp.TYPE_BICUBIC;

}
