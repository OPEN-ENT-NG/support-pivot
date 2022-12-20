package fr.openent.supportpivot.model.lde;

import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.helpers.IModelHelper;
import fr.openent.supportpivot.model.IModel;
import fr.openent.supportpivot.model.pivot.PivotPJ;
import io.vertx.core.json.JsonObject;

public class LdePj implements IModel<LdePj> {
    private String nom;
    private String contenu;

    public LdePj(JsonObject jsonObject) {
        this.nom = jsonObject.getString(Field.NOM);
        this.contenu = jsonObject.getString(Field.CONTENU);
    }

    public LdePj(PivotPJ pivotPJ) {
        this.nom = pivotPJ.getNom();
        this.contenu = pivotPJ.getContenu().replace("\r\n", "");
    }

    @Override
    public JsonObject toJson() {
        return IModelHelper.toJson(this, false, true);
    }

    public String getNom() {
        return nom;
    }

    public LdePj setNom(String nom) {
        this.nom = nom;
        return this;
    }

    public String getContenu() {
        return contenu;
    }

    public LdePj setContenu(String contenu) {
        this.contenu = contenu;
        return this;
    }
}
