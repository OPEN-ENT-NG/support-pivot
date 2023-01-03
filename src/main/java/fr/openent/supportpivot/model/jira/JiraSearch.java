package fr.openent.supportpivot.model.jira;

import fr.openent.supportpivot.model.ISearchTicket;

public class JiraSearch implements ISearchTicket {
    private String idJira;
    private String idExterne;
    private String idEnt;
    private String date;

    public String getIdJira() {
        return idJira;
    }

    public JiraSearch setIdJira(String idJira) {
        this.idJira = idJira;
        return this;
    }

    public String getIdExterne() {
        return idExterne;
    }

    public JiraSearch setIdExterne(String idExterne) {
        this.idExterne = idExterne;
        return this;
    }

    public String getIdEnt() {
        return idEnt;
    }

    public JiraSearch setIdEnt(String idEnt) {
        this.idEnt = idEnt;
        return this;
    }

    public String getDate() {
        return date;
    }

    public JiraSearch setDate(String date) {
        this.date = date;
        return this;
    }
}
