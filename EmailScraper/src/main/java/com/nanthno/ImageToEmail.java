package com.nanthno;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImageToEmail {

    AffineTransformOp scaleOp;

    int numLineFails = 0;
    int numLineSucceed = 0;
    int numLineRepaired = 0;
    int numLineFailedToRepair = 0;

    Flags flags;

    // pattern to find emails
    static final Pattern emailPattern = Pattern.compile("[^@ \\t\\r\\n]+@[^@ \\t\\r\\n]+\\.[^@ \\t\\r\\n]+");


    ImageToEmail(Flags flags) {
        this.flags = flags;


        AffineTransform at = new AffineTransform();
        at.scale(flags.scaleFactor, flags.scaleFactor);
        scaleOp = new AffineTransformOp(at, flags.affineTransformOp);
    }

    public ArrayList<EmailData> imageToEmailData(File imgFile) {
        try {

            BufferedImage img = ImageIO.read(imgFile);

            String text = imageToString(img);

            if (flags.printLong)
                System.out.println(String.format("%s\n%s\n%s\n%s", imgFile.getName(), "-".repeat(40), text, "-".repeat(40)));

            ArrayList<EmailData> data = findAllEmailDataInString(text, imgFile.getName());

            return data;
        } catch (TesseractException | IOException | URISyntaxException e) {
            e.printStackTrace();
        }
        return new ArrayList<EmailData>();
    }

    private BufferedImage preprocessImage(BufferedImage img) {

        if (flags.scaleFactor != 1)
            System.out.println("ERROR: SCALE NOT IMPLEMENTED");

        if (flags.color_mode != 0)
            img = colorPreproc(img);

        return img;
    }


    private BufferedImage colorPreproc(BufferedImage img) {
        // binerize or grayscale
        int black = Color.black.getRGB();
        int white = Color.white.getRGB();
        for (int x = 0; x < img.getWidth(); x++) {
            for (int y = 0; y < img.getHeight(); y++) {
                int p = img.getRGB(x, y);

                int r = (p >> 16) & 0xff;
                int g = (p >> 8) & 0xff;
                int b = p & 0xff;

                int avg = (r + g + b) / 3;

                if (flags.color_mode == Flags.GRAY_SCALE) {
                    img.setRGB(x, y, avg);
                } else if (flags.color_mode == Flags.BINERIZE) {

                    if (avg < flags.binerizeThreshold) {
                        img.setRGB(x, y, black);
                    } else {
                        img.setRGB(x, y, white);
                    }
                }
            }
        }
        return img;
    }

    private String imageToString(BufferedImage img) throws TesseractException, URISyntaxException {

        String path = new File(Main.class.getProtectionDomain().getCodeSource().getLocation()
                .toURI()).getParentFile().getPath();

        Tesseract tesseract = new Tesseract();

        tesseract.setDatapath(path);
        tesseract.setLanguage("eng");


        img = preprocessImage(img);

        //saveImage(img, "postproc.png");

        String text = tesseract.doOCR(img);


        // path of your image file
        return (text);
    }

    private ArrayList<EmailData> findAllEmailDataInString(String text, String fileName) {

        // pattern to find all lines with something in them
        Pattern linePattern = Pattern.compile("^.+$");
        Matcher lineMatcher = linePattern.matcher(text);

        ArrayList<EmailData> emails = new ArrayList<EmailData>();

        for (String line : text.split("[\\r\\n]+")) {
            try {
                line = line.replaceAll("\\s+", " ");
                if (flags.printLong)
                    System.out.println("\nParsing " + line);
                Matcher emailMatcher = emailPattern.matcher(line);
                boolean foundEmail = emailMatcher.find();
                if (flags.printLong) System.out.println("Found email: " + String.valueOf(foundEmail));


                String[] words = line.split(" ");
                if (words.length < 2) {
                    Logging.logWarning("Failed to parse line because there were not enough words: " + line);
                    continue;
                }

                //needsRepair &= words[0].toLowerCase().equals(words[1].toLowerCase());
                boolean lastStartsWithFirst = words[1].toLowerCase().startsWith(words[0].toLowerCase());
                boolean lastIsEmail = foundEmail && words[1].equals(line.substring(emailMatcher.start(), emailMatcher.end()));

                boolean needsRepair = !foundEmail | (lastStartsWithFirst & !lastIsEmail);

                if (flags.printLong) {
                    System.out.println("Starts with first: " + words[1].toLowerCase().startsWith(words[0].toLowerCase()));
                    System.out.println("Last is email: " + lastIsEmail);
                    System.out.println("Needs Repair: " + String.valueOf(needsRepair));
                }
                if (needsRepair) {
                    EmailData emailData = repairLineToEmailData(line, fileName);
                    if (emailData == null) {
                        numLineFailedToRepair++;
                        Logging.logWarning(String.format("Failed Repair of %s in %s", line, fileName));
                        continue;
                    }
                    Logging.logInfo(String.format("Repaired %s =>> %s", line, emailData.toString()));
                    emails.add(emailData);
                    numLineSucceed++;
                    numLineRepaired++;
                } else {
                    String email = line.substring(emailMatcher.start(), emailMatcher.end()).trim();
                    String name = line.substring(0, emailMatcher.start()).trim();
                    String company = line.substring(emailMatcher.end()).trim();

                    String[] nameWords = name.split(" ");
                    String notes = "";
                    if (nameWords.length >= 2) {
                        if (getWordsDelta(nameWords[0], nameWords[1]) >= flags.wordsSimilarDelta) {
                            notes += "Word Delta Similar: last name might be part of email with typo";
                        }
                    }

                    EmailData data = new EmailData(email, name, company, fileName, notes);

                    emails.add(data);
                    numLineSucceed++;
                }
            } catch (Exception e) {
                String out = String.format("Encountered the following exception while processing %s in %s", line, fileName);
                e.printStackTrace();
                Logging.logPrint(Level.SEVERE, out + "\n" + e.toString());
            }
        }

        return emails;
    }


    // attempts to repair a line
    Pattern extensionMatcher = Pattern.compile("(com|net|org|edu|gov|it)\\s|$");

    EmailData repairLineToEmailData(String origLine, String fileName) {
        if (flags.printLong)
            System.out.println("Attempting to repair " + origLine);
        String line = origLine;
        String[] words = line.split(" ");
        if (words.length < 2) {
            return null;
        }
        // find name
        String first = words[0];
        String last = words[1];
        String name;
        boolean noLast = last.toLowerCase().startsWith(first.toLowerCase()) || last.contains("@");
        if (noLast) {
            // name is first word
            name = line.substring(0, line.indexOf(" ")).trim();
        } else {
            if (words.length < 3) {
                Logging.logWarning(String.format("Unable to parse %s in %s because insufficient number of words", line, fileName));
                return null;
            }
            // name is first and second word
            if (line.contains(" ") && line.substring(line.indexOf(" ")).contains(" ")) {
                name = line.substring(0, line.indexOf(" ", line.indexOf(" ") + 1)).trim();
            } else {
                if (flags.printLong) {
                    System.out.println("Failed to parse line because name issues");
                }
                Logging.logWarning(String.format("Failed to repair %s because of issues splitting the name from the rest of the line", origLine));
                return null;
            }
        }
        if (flags.printLong) {
            System.out.println("Repair: noLast = " + noLast);
            System.out.println("Repair: Name = " + name);
        }
        // trim line to not contain name
        line = line.substring(name.length()).trim();

        // repair disconnects
        Matcher matcher = extensionMatcher.matcher(line);
        String newLine = "";
        if (matcher.find()) {
            newLine = dotConnect(line, 0, matcher.end());
        } else if (line.contains("@")) {
            newLine = dotConnect(line, 0, line.indexOf("@"));
        }
        if (newLine.length() > 0 && !newLine.equals(line)) {
            line = newLine;
        }

        // repair missing "@"
        String email;
        String company;
        if (line.contains(" ")) {
            email = line.substring(0, line.indexOf(" ")).trim();
            company = line.substring(line.indexOf(" ")).trim();
        } else {
            email = line;
            company = "";
        }
        if (flags.printLong)
            System.out.println("Repair: email found as " + email);
        if (!email.contains(("@"))) {
            int lastIndex = email.indexOf(last);
            int firstIndex = email.indexOf(first);
            if (lastIndex > -1 || firstIndex > -1) {
                int lastEnd = lastIndex + last.length();
                int firstEnd = firstIndex + first.length();
                int greater = lastEnd > firstEnd ? lastEnd : firstEnd;
                Character charAt = email.charAt(greater);
                if (charAt == 'e' || charAt == 'a') {
                    email = email.substring(0, greater) + '@' + email.substring(greater + 1);
                    if (flags.printLong) {
                        System.out.println("Repaired missing '@': " + email);
                    }
                } else
                    return null;
            }
        }

        Matcher emailMatcher = emailPattern.matcher(email);
        if (!emailMatcher.find())
            return null;


        EmailData emailData = new EmailData(email, name, company, fileName, "Repaired from " + origLine);
        if (flags.printLong) {
            System.out.println("repaired to " + emailData.toString());
        }

        return emailData;

    }

    // replaces all whitespace or whitespace with "." next to it with a "." as long as it is not at the end of the mid
    String dotConnect(String line, int start, int end) {
        String pre = line.substring(0, start);
        String mid = line.substring(start, end);
        String post = line.substring(end);
        mid = mid.replaceAll("(( \\.)|(\\. )|( ))(?!$)", ".");
        String output = pre + mid + post;
        if (flags.printLong) {
            System.out.println(String.format("Dot connect %s ->> mid: %s ->> %s", line, mid, output));
        }
        return output;
    }

    int getWordsDelta(String word1, String word2) {
        int len1 = word1.length();
        int len2 = word2.length();
        int len = len1 > len2 ? len2 : len1;
        int delta = Math.abs(len2 - len1);
        for (int i = 0; i < len; i++) {
            if (word1.charAt(i) != word2.charAt(i))
                delta++;
        }
        return delta;
    }
//
//    String repairLineOld(String line) {
//        String origLine = line;
//
//        ArrayList<String> words = new ArrayList<String>(Arrays.asList(line.split(" ")));
//        if (words.size() < 4)
//            return null;
//
//        // repair name . missing
//        String first = words.get(0).toLowerCase();
//        String last = words.get(1).toLowerCase();
//        int offset = 0;
//        if (last.contains("@") || last.toLowerCase().equals(first.toLowerCase())) {
//            if (flags.printLong) System.out.println("Repair: last is email fixed");
//            offset = 1;
//            last = "";
//        }
//
//        // repair disconnected first
//        String email = words.get(2 - offset);
//        if (!email.contains("@")) {
//            if (email.startsWith(first)) {
//                if (words.get(3 - offset).startsWith(last)) {
//                    String start = words.get(2 - offset);
//                    if (!start.endsWith("."))
//                        start += ".";
//                    email = start + words.get(3 - offset);
//                    words.remove(3 - offset);
//                }
//            }
//        }
//
//
//
//        Matcher matcher = extensionMatcher.matcher(line);
//        // repair missing @
//        if (!email.contains("@") && email.length() > last.length()) {
//            Character atChar = email.charAt(last.length());
//            if (atChar == 'a' || atChar == 'e') {
//                email = email.substring(0, last.length()) + "@" + email.substring(last.length() + 1);
//            } else {
//                return null;
//            }
//        }
//
//        words.set(2 - offset, email);
//
//        line = String.join(" ", words);
//        // repair disconnected extension
//        Matcher matcher = extensionMatcher.matcher(line);
//        if (matcher.find(first.length() + last.length() + email.length())) {
//
//            int matchIndex = matcher.start();
//            String preAt = line.substring(0, line.indexOf("@"));
//            String postMatch = line.substring(matchIndex);
//            String mid = line.substring(line.indexOf("@"), matchIndex);
//            mid = mid.replaceAll("( \\.)|(\\. )|( )", "."); // close all gaps with a "." between
//            line = preAt + mid + postMatch;
//        }
//
//        if (origLine.equals(line)) {
//            return null;
//        }
//        if (flags.printLong)
//            System.out.println(String.format("Repaired %s ==> %s", origLine, line));
//
//        return line + ";repaired;from " + origLine;
//    }

    private void saveImage(BufferedImage img, String filePath) {
        try {
            File f = new File(filePath);
            ImageIO.write(img, "png", f);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
