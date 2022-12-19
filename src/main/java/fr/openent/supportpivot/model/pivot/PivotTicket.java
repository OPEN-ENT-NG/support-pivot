package fr.openent.supportpivot.model.pivot;

import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.helpers.IModelHelper;
import fr.openent.supportpivot.model.IModel;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PivotTicket implements IModel<PivotTicket>, Cloneable{
    private String idJira;
    private String statutJira;
    private String dateResolutionJira;
    private String idEnt;
    private String statutEnt;
    private String dateResolutionEnt;
    private String idExterne;
    private String statutExterne;
    private String collectivite;
    private String academie;
    private String creation;
    private String maj;
    private String demandeur;
    private String typeDemande;
    private String titre;
    private String uai;
    private String description;
    private String priorite;
    private List<String> module;
    private List<String> commentaires;
    //Todo delete doublon creation
    private String dateCreation;
    private String attribution;
    private List<PivotPJ> pj;

    public PivotTicket(JsonObject jsonObject) {
        this.idJira = jsonObject.getString(Field.ID_JIRA);
        this.collectivite = jsonObject.getString(Field.COLLECTIVITE);
        this.academie = jsonObject.getString(Field.ACADEMIE);
        this.creation = jsonObject.getString(Field.CREATION);
        this.maj = jsonObject.getString(Field.MAJ);
        this.demandeur = jsonObject.getString(Field.DEMANDEUR);
        this.typeDemande = jsonObject.getString(Field.TYPE_DEMANDE);
        this.titre = jsonObject.getString(Field.TITRE);
        this.uai = jsonObject.getString(Field.UAI);
        this.description = jsonObject.getString(Field.DESCRIPTION);
        this.priorite = jsonObject.getString(Field.PRIORITE);
        this.module = jsonObject.getJsonArray(Field.MODULES, new JsonArray()).stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(Collectors.toList());
        this.idEnt = jsonObject.getString(Field.ID_ENT);
        this.commentaires = jsonObject.getJsonArray(Field.COMMENTAIRES, new JsonArray()).stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(Collectors.toList());
        this.statutEnt = jsonObject.getString(Field.STATUT_ENT);
        this.statutJira = jsonObject.getString(Field.STATUT_JIRA);
        this.dateCreation = jsonObject.getString(Field.DATE_CREATION);
        this.idExterne = jsonObject.getString(Field.ID_EXTERNE);
        this.statutExterne = jsonObject.getString(Field.STATUT_EXTERNE);
        this.attribution = jsonObject.getString(Field.ATTRIBUTION);
        this.pj = IModelHelper.toList(jsonObject.getJsonArray(Field.PJ, new JsonArray()), PivotPJ.class);
        this.dateResolutionJira = jsonObject.getString(Field.DATE_RESOLUTION_JIRA);
        this.dateResolutionEnt = jsonObject.getString(Field.DATE_RESOLUTION_ENT);
    }

    public PivotTicket() {
    }

    @Override
    public JsonObject toJson() {
        return IModelHelper.toJson(this, true, true);
    }

    public String getIdJira() {
        return idJira;
    }

    public PivotTicket setIdJira(String idJira) {
        this.idJira = idJira;
        return this;
    }

    public String getCollectivite() {
        return collectivite;
    }

    public PivotTicket setCollectivite(String collectivite) {
        this.collectivite = collectivite;
        return this;
    }

    public String getAcademie() {
        return academie;
    }

    public PivotTicket setAcademie(String academie) {
        this.academie = academie;
        return this;
    }

    public String getCreation() {
        return creation;
    }

    public PivotTicket setCreation(String creation) {
        this.creation = creation;
        return this;
    }

    public String getMaj() {
        return maj;
    }

    public PivotTicket setMaj(String maj) {
        this.maj = maj;
        return this;
    }

    public String getDemandeur() {
        return demandeur;
    }

    public PivotTicket setDemandeur(String demandeur) {
        this.demandeur = demandeur;
        return this;
    }

    public String getTypeDemande() {
        return typeDemande;
    }

    public PivotTicket setTypeDemande(String typeDemande) {
        this.typeDemande = typeDemande;
        return this;
    }

    public String getTitre() {
        return titre;
    }

    public PivotTicket setTitre(String titre) {
        this.titre = titre;
        return this;
    }

    public String getUai() {
        return uai;
    }

    public PivotTicket setUai(String uai) {
        this.uai = uai;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public PivotTicket setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getPriorite() {
        return priorite;
    }

    public PivotTicket setPriorite(String priorite) {
        this.priorite = priorite;
        return this;
    }

    public List<String> getModule() {
        return module;
    }

    public PivotTicket setModule(List<String> module) {
        this.module = module;
        return this;
    }

    public String getIdEnt() {
        return idEnt;
    }

    public PivotTicket setIdEnt(String idEnt) {
        this.idEnt = idEnt;
        return this;
    }

    public List<String> getCommentaires() {
        return commentaires;
    }

    public PivotTicket setCommentaires(List<String> commentaires) {
        this.commentaires = commentaires;
        return this;
    }

    public String getStatutEnt() {
        return statutEnt;
    }

    public PivotTicket setStatutEnt(String statutEnt) {
        this.statutEnt = statutEnt;
        return this;
    }

    public String getStatutJira() {
        return statutJira;
    }

    public PivotTicket setStatutJira(String statutJira) {
        this.statutJira = statutJira;
        return this;
    }

    public String getDateCreation() {
        return dateCreation;
    }

    public PivotTicket setDateCreation(String dateCreation) {
        this.dateCreation = dateCreation;
        return this;
    }

    public String getIdExterne() {
        return idExterne;
    }

    public PivotTicket setIdExterne(String idExterne) {
        this.idExterne = idExterne;
        return this;
    }

    public String getStatutExterne() {
        return statutExterne;
    }

    public PivotTicket setStatutExterne(String statutExterne) {
        this.statutExterne = statutExterne;
        return this;
    }

    public String getAttribution() {
        return attribution;
    }

    public PivotTicket setAttribution(String attribution) {
        this.attribution = attribution;
        return this;
    }

    public List<PivotPJ> getPj() {
        return pj;
    }

    public PivotTicket setPj(List<PivotPJ> pj) {
        this.pj = pj;
        return this;
    }

    public String getDateResolutionJira() {
        return dateResolutionJira;
    }

    public PivotTicket setDateResolutionJira(String dateResolutionJira) {
        this.dateResolutionJira = dateResolutionJira;
        return this;
    }

    public String getDateResolutionEnt() {
        return dateResolutionEnt;
    }

    public PivotTicket setDateResolutionEnt(String dateResolutionEnt) {
        this.dateResolutionEnt = dateResolutionEnt;
        return this;
    }

    @Override
    public PivotTicket clone() {
        try {
            PivotTicket clone = (PivotTicket) super.clone();
            clone.setModule(new ArrayList<>(this.module));
            clone.setCommentaires(new ArrayList<>(this.commentaires));
            clone.setPj(this.getPj().stream().map(PivotPJ::clone).collect(Collectors.toList()));
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
