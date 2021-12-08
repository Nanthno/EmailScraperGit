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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImageToEmail {

    AffineTransformOp scaleOp;

    int numLineFails = 0;
    int numLineSucceed = 0;
    int numLineRepaired = 0;

    Flags flags;

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
        // pattern to find emails
        Pattern emailPattern = Pattern.compile("[^@ \\t\\r\\n]+@[^@ \\t\\r\\n]+\\.[^@ \\t\\r\\n]+");

        // pattern to find all lines with something in them
        Pattern linePattern = Pattern.compile("^.+$");
        Matcher lineMatcher = linePattern.matcher(text);

        ArrayList<EmailData> emails = new ArrayList<EmailData>();

        for (String line : text.split("[\\r\\n]+")) {
            Matcher emailMatcher = emailPattern.matcher(line);
            boolean foundEmail = emailMatcher.find();


            String[] words = line.split(" ");
            if (!foundEmail || words[0].toLowerCase().equals(words[1].toLowerCase())) {
                String newLine = repairLine(line);
                if (newLine != null) {
                    line = newLine;
                    emailMatcher = emailPattern.matcher(line);
                    foundEmail = emailMatcher.find();
                    if (foundEmail)
                        numLineRepaired += 1;
                }
            }
            if (foundEmail) {
                String email = line.substring(emailMatcher.start(), emailMatcher.end()).trim();
                String name = line.substring(0, emailMatcher.start()).trim();
                String company = line.substring(emailMatcher.end()).trim();

                EmailData data = new EmailData(email, name, company);

                emails.add(data);
                numLineSucceed++;
            } else {
                numLineFails++;
                if (flags.printLong)
                    System.out.println(String.format("Unable to parse line in %s: %s", fileName, line));
            }
        }
        return emails;
    }

    // attempts to repair a line
    Pattern extensionMatcher = Pattern.compile("(com|net|org|edu|gov)");

    String repairLine(String line) {
        String origLine = line;

        ArrayList<String> words = new ArrayList<String>(Arrays.asList(line.split(" ")));
        if (words.size() < 4)
            return null;

        // repair name . missing
        String first = words.get(0).toLowerCase();
        String last = words.get(1).toLowerCase();
        int offset = 0;
        if (last.contains("@") || last.toLowerCase().equals(first.toLowerCase())) {
            offset = 1;
            last = "";
        }

        String email = words.get(2 - offset);
        if (!email.contains("@")) {
            if (email.startsWith(first)) {
                if (words.get(3 - offset).startsWith(last)) {
                    email = words.get(2 - offset) + "." + words.get(3 - offset);
                    words.remove(3 - offset);
                }
            }
        }

        // repair missing @
        if (!email.contains("@") && email.length() > last.length()) {
            Character atChar = email.charAt(last.length());
            if (atChar == 'a' || atChar == 'e') {
                email = email.substring(0, last.length()) + "@" + email.substring(last.length() + 1);
            } else {
                return null;
            }
        }

        words.set(2 - offset, email);

        line = String.join(" ", words);
        // repair disconnected extension
        Matcher matcher = extensionMatcher.matcher(line);
        if (matcher.find(first.length() + last.length() + email.length())) {

            int matchIndex = matcher.start();
            String preAt = line.substring(0, line.indexOf("@"));
            String postMatch = line.substring(matchIndex);
            String mid = line.substring(line.indexOf("@"), matchIndex);
            mid = mid.replaceAll(" ", ".");
            line = preAt + mid + postMatch;
        }

        if (origLine.equals(line)) {
            return null;
        }
        if (flags.printLong)
            System.out.println(String.format("Repaired %s ==> %s", origLine, line));

        return line + ";repaired;from " + origLine;
    }

    private void saveImage(BufferedImage img, String filePath) {
        try {
            File f = new File(filePath);
            ImageIO.write(img, "png", f);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
