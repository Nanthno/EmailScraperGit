package com.nanthno;

import me.tongfei.progressbar.ProgressBar;

import java.awt.image.AffineTransformOp;
import java.io.File;
import java.util.ArrayList;
import java.util.logging.Level;

public class Main {

    public static void main(String[] args) {
        Logging.init();

        Logging.logInfo("start");


        Flags flags = new Flags(args);

        int numLineFails = 0;
        int numLineSucceed = 0;
        int numLineRepaired = 0;
        int numLineFailedToRepair = 0;


        File sourceDirectory = new File(flags.directoryPath);
        if (!sourceDirectory.exists()) {

            System.out.println(String.format("Error: source directory %s does not exist", sourceDirectory.getPath()));

            sourceDirectory = new File(".");
            System.out.println(sourceDirectory.getAbsolutePath());
            System.exit(1);
        }
        File[] files = sourceDirectory.listFiles((dir, name) -> name.endsWith(".png"));
        Logging.logPrintInfo(String.format("Found %d images", files.length));

        // split files into batches
        ArrayList<File>[] fileBatches = new ArrayList[flags.numThreads];
        for (int i = 0; i < flags.numThreads; i++) {
            fileBatches[i] = new ArrayList<File>();
        }
        for (int i = 0; i < files.length; i++) {
            fileBatches[i % flags.numThreads].add(files[i]);
        }

        try (ProgressBar progressBar = new ProgressBar("", files.length)) {
            Logging.logPrintInfo(String.format("Launching %d threads", flags.numThreads));
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
                    numLineFailedToRepair += thread.numLineFailedToRepair;
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

            CsvExporting.saveEmailDataToCsv(allEmails.stream().toArray(EmailData[]::new), flags.outputName);
        }
        Logging.logPrintInfo(String.format("Failed to read %d lines", numLineFails));
        Logging.logPrintInfo(String.format("Succeeded reading %d lines", numLineSucceed));
        Logging.logPrintInfo(String.format("Of which %d lines were repaired", numLineRepaired));
        Logging.logPrintInfo(String.format("%d lines could not be repaired", numLineFailedToRepair));
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
    int numLineFailedToRepair = 0;

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
            numLineFailedToRepair += imageToEmail.numLineFailedToRepair;

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
    int numThreads = 8;
    static int color_mode = 1;
    static int binerizeThreshold = 220;
    static int scaleFactor = 1;
    static int affineTransformOp = AffineTransformOp.TYPE_BICUBIC;
    String directoryPath = ".";
    String outputName = "output";
    int wordsSimilarDelta = 3;

    Flags(String[] args) {
        boolean foundDir = false;
        boolean foundOutput = false;

        for (int i = 0; i < args.length; i++) {
            Logging.logInfo(String.valueOf(i));
            String arg = args[i];
            if (arg.equals("-tc")) { // set thread count
                i++;
                String numThreadsString = args[i];
                if (!numThreadsString.matches("\\d+")) {
                    Logging.logPrint(Level.SEVERE, "ERROR: Number of threads must be a positive integer");
                    System.exit(1);
                }
                Logging.logInfo("set numThreads to " + numThreadsString);
                numThreads = Integer.valueOf(numThreadsString);
            } else if (arg.equals("-wd")) { // set thread count
                i++;
                String numThreadsString = args[i];
                if (!numThreadsString.matches("\\d+")) {
                    Logging.logPrint(Level.SEVERE, "ERROR: Word delta must be a positive integer");
                    System.exit(1);
                }
                Logging.logInfo("set numThreads to " + numThreadsString);
                wordsSimilarDelta = Integer.valueOf(numThreadsString);
            } else if (arg.equals("-l")) {
                printLong = true;
            } else if (args[i].startsWith("-")) { // invalid flag
                Logging.logPrint(Level.SEVERE, String.format("ERROR: Flag %s not recognized.\nPlease refer to the README for valid flags", arg.substring(1)));
                System.exit(1);
            } else { // ordered arg
                if (foundDir) {
                    Logging.logInfo("Set outputName to " + args[i]);
                    outputName = args[i];
                    foundOutput = true;
                } else {
                    Logging.logInfo("Set directoryPath to " + args[i]);
                    directoryPath = args[i];
                    foundDir = true;
                }
            }
        }
    }

}
