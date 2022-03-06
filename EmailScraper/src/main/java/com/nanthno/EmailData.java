package com.nanthno;

class EmailData {
    String email;
    String name;
    String company;
    String notes = "";
    String file;

    EmailData(String email, String name, String company, String file) {
        this.email = email;
        this.name = name;
        this.company = company;
        this.file = file;
    }

    EmailData(String email, String name, String company, String file, String notes) {
        this.email = email;
        this.name = name;
        this.company = company;
        this.notes = notes;
        this.file = file;
    }


    public String toString() {
        return String.format("%s;%s;%s;%s;%s", name, email, company, file, notes);
    }
}
