package fr.openent.supportpivot.model.lde;

import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.helpers.IModelHelper;
import fr.openent.supportpivot.model.IModel;
import fr.openent.supportpivot.model.IPivotTicket;
import fr.openent.supportpivot.model.pivot.PivotTicket;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.stream.Collectors;

public class LdeTicket implements IModel<LdeTicket>, IPivotTicket {
    private String idJira;
    private String statutJira;
    private String idExterne;
    private String statutExterne;
    private List<String> commentaires;
    private String demandeur;
    private String typeDemande;
    private String titre;
    private String uai;
    private String description;
    private List<LdePj> pj;
    private String creation;
    private String maj;

    public LdeTicket(JsonObject jsonObject) {
        this.idJira = jsonObject.getString(Field.ID_JIRA);
        this.statutJira = jsonObject.getString(Field.STATUT_JIRA);
        this.idExterne = jsonObject.getString(Field.ID_EXTERNE);
        this.statutExterne = jsonObject.getString(Field.STATUT_EXTERNE);
        this.commentaires = jsonObject.getJsonArray(Field.COMMENTAIRES).stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(Collectors.toList());
        this.demandeur = jsonObject.getString(Field.DEMANDEUR);
        this.typeDemande = jsonObject.getString(Field.TYPE_DEMANDE);
        this.titre = jsonObject.getString(Field.TITRE);
        this.uai = jsonObject.getString(Field.UAI);
        this.description = jsonObject.getString(Field.DESCRIPTION);
        this.pj = IModelHelper.toList(jsonObject.getJsonArray(Field.PJ, new JsonArray()), LdePj.class);
        this.creation = jsonObject.getString(Field.CREATION);
        this.maj = jsonObject.getString(Field.MAJ);
    }

    public LdeTicket(PivotTicket pivotTicket) {
        this.idJira = pivotTicket.getIdJira();
        this.statutJira = pivotTicket.getStatutJira();
        this.idExterne = pivotTicket.getIdExterne();
        this.statutExterne = pivotTicket.getStatutExterne();
        this.commentaires = pivotTicket.getCommentaires();
        this.demandeur = pivotTicket.getDemandeur();
        this.typeDemande = pivotTicket.getTypeDemande();
        this.titre = pivotTicket.getTitre();
        this.uai = pivotTicket.getUai();
        this.description = pivotTicket.getDescription();
        this.creation = pivotTicket.getCreation();
        this.maj = pivotTicket.getMaj();

        if (pivotTicket.getPj() != null) {
            this.pj = pivotTicket.getPj()
                    .stream()
                    .map(LdePj::new)
                    .collect(Collectors.toList());
        }
    }

    public LdeTicket listFormat() {
        this.statutJira = null;
        this.idExterne = null;
        this.statutExterne = null;
        this.commentaires = null;
        this.demandeur = null;
        this.typeDemande = null;
        this.titre = null;
        this.uai = null;
        this.description = null;
        this.pj = null;
        return this;
    }

    @Override
    public JsonObject toJson() {
        return IModelHelper.toJson(this, true, true);
    }

    public String getIdJira() {
        return idJira;
    }

    public LdeTicket setIdJira(String idJira) {
        this.idJira = idJira;
        return this;
    }

    public String getStatutJira() {
        return statutJira;
    }

    public LdeTicket setStatutJira(String statutJira) {
        this.statutJira = statutJira;
        return this;
    }

    public String getIdExterne() {
        return idExterne;
    }

    public LdeTicket setIdExterne(String idExterne) {
        this.idExterne = idExterne;
        return this;
    }

    public String getStatutExterne() {
        return statutExterne;
    }

    public LdeTicket setStatutExterne(String statutExterne) {
        this.statutExterne = statutExterne;
        return this;
    }

    public List<String> getCommentaires() {
        return commentaires;
    }

    public LdeTicket setCommentaires(List<String> commentaires) {
        this.commentaires = commentaires;
        return this;
    }

    public String getDemandeur() {
        return demandeur;
    }

    public LdeTicket setDemandeur(String demandeur) {
        this.demandeur = demandeur;
        return this;
    }

    public String getTypeDemande() {
        return typeDemande;
    }

    public LdeTicket setTypeDemande(String typeDemande) {
        this.typeDemande = typeDemande;
        return this;
    }

    public String getTitre() {
        return titre;
    }

    public LdeTicket setTitre(String titre) {
        this.titre = titre;
        return this;
    }

    public String getUai() {
        return uai;
    }

    public LdeTicket setUai(String uai) {
        this.uai = uai;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public LdeTicket setDescription(String description) {
        this.description = description;
        return this;
    }

    public List<LdePj> getPj() {
        return pj;
    }

    public LdeTicket setPj(List<LdePj> pj) {
        this.pj = pj;
        return this;
    }

    public String getCreation() {
        return creation;
    }

    public LdeTicket setCreation(String creation) {
        this.creation = creation;
        return this;
    }

    public String getMaj() {
        return maj;
    }

    public LdeTicket setMaj(String maj) {
        this.maj = maj;
        return this;
    }
}
