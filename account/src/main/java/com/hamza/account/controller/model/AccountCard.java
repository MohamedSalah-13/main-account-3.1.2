package com.hamza.account.controller.model;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AccountCard {

    private int id;
    private String name;
    private String date;
    private double purchase;
    private double paid;
    private double details;
    private String notes;
    private String information;

    public AccountCard() {
    }

    public AccountCard(int id, String name, String date, double purchase, double paid, double details, String notes, String information) {
        this.id = id;
        this.name = name;
        this.date = date;
        this.purchase = purchase;
        this.paid = paid;
        this.details = details;
        this.notes = notes;
        this.information = information;
    }
}
