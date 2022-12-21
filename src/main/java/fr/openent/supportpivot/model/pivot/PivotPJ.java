package fr.openent.supportpivot.model.pivot;

import fr.openent.supportpivot.constants.Field;
import fr.openent.supportpivot.helpers.IModelHelper;
import fr.openent.supportpivot.model.IModel;
import io.vertx.core.json.JsonObject;

public class PivotPJ implements IModel<PivotPJ>, Cloneable {
    private String nom;
    private String contenu;

    public PivotPJ(JsonObject jsonObject) {
        this.nom = jsonObject.getString(Field.NOM);
        this.contenu = jsonObject.getString(Field.CONTENU);
    }

    public PivotPJ() {
    }

    @Override
    public JsonObject toJson() {
        return IModelHelper.toJson(this, true, true);
    }

    public String getNom() {
        return nom;
    }

    public PivotPJ setNom(String nom) {
        this.nom = nom;
        return this;
    }

    public String getContenu() {
        return contenu;
    }

    public PivotPJ setContenu(String contenu) {
        this.contenu = contenu;
        return this;
    }

    @Override
    public PivotPJ clone() {
        try {
            return (PivotPJ) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
