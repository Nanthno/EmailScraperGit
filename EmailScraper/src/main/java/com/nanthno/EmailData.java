package com.nanthno;

class EmailData {
    String email;
    String name;
    String company;


    EmailData(String email, String name, String company) {
        this.email = email;
        this.name = name;
        this.company = company;
    }


    public String toString() {
        return String.format("%s;%s;%s", name, email, company);
    }
}
