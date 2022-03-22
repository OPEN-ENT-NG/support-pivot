/*
 *
 * Copyright (c) Mairie de Paris, CGI, 2016.
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package fr.openent.supportpivot;

import fr.openent.supportpivot.controllers.JiraController;
import fr.openent.supportpivot.controllers.LdeController;
import fr.openent.supportpivot.controllers.SupportController;
import fr.openent.supportpivot.managers.ConfigManager;
import fr.openent.supportpivot.managers.ServiceManager;
import org.entcore.common.http.BaseServer;

import java.util.HashMap;
import java.util.Map;

public class Supportpivot extends BaseServer {

	/**
	 * Encode a string in UTF-8
	 * @param in String to encode
	 * @return encoded String
	 */
	private static String stringEncode(String in) {
		return  in;
	}

	public final static Map<String, String> applicationsMap = new HashMap<String, String>()
	{
		{
			put("/actualites","Actualités");
			put("/support",stringEncode("Aide et Support"));
			put("/archive",stringEncode("Archive"));//non trouvé dans JIRA
			put("/blog",stringEncode("Blog"));
			put("/calendar",stringEncode("Agenda"));
			put("/collaborativeeditor",stringEncode("Pad"));
			put("/collaborativewall",stringEncode("Mur collaboratif"));
			put("/competences",stringEncode("Compétences"));
			put("/diary",stringEncode("Cahier de texte"));//non trouvé dans JIRA
			put("/edt",stringEncode("EDT"));//non trouvé dans JIRA
			put("/workspace/workspace",stringEncode("Espace_Documenataire"));//a revoir dans JIRA
			put("/exercizer",stringEncode("Exercizer"));//non trouvé dans JIRA
			put("https://folios.onisep.fr/","Folios");//non trouvé dans Jira
			put("/forum",stringEncode("Forum"));//non trouvé dans JIRa
			put("/auth/saml/sso/urn:gar:ihmAffectation:seclin",stringEncode("GAR"));//a mettre ?
			put("/homework-assistance",stringEncode("Homework-assistance"));//pas dans JIRA
			put("/incidents", stringEncode("Incidents"));
			put("https://www.kiosque-edu.com/","KNE");//non trouvé dans JIRA
			put("/mediacentre",stringEncode("/mediacentre"));//non trouvé dans JIRA
			put("/conversation/conversation",stringEncode("Messagerie"));
			put("/mindmap",stringEncode("Carte mentale"));//non trouvé dans JIRA
			put("/pages",stringEncode("Pages"));//Pages seulement ?
			put("/presences", stringEncode("Présences"));// non trouvé dans JIRA
			put("https://www.index-education.com/fr/pronote-info191-pronote-a-chacun-son-espace--demonstration.php","pronote");
			put("/rack",stringEncode("Casier"));//non trouvé dans JIRA
			put("/rbs",stringEncode("Réservation_ressources"));
			put("/timeline/timeline",stringEncode("Fil de nouveauté"));
			put("/timelinegenerator",stringEncode("Frise chronologique"));//etiquette timelinegenerator
			put("/wiki",stringEncode("wikis"));


			/*put("/eliot/absences",stringEncode("Absences (Axess)"));//?

			put("/admin",stringEncode("Administration"));

			put("/eliot/agenda",stringEncode("Agenda (Axess)"));

			put("/userbook/annuaire#/search",stringEncode("Annuaire"));

			put("/eliot/textes",stringEncode("Cahier de textes (Axess)"));


			put("/community",stringEncode("Communaut&eacute;"));
			put("/cas",stringEncode("Connexion"));






			put("/eliot/notes",stringEncode("Notes (Axess)"));


			put("/eliot/scolarite",stringEncode("Scolarité (Axess)"));
			put("/poll",stringEncode("Sondage"));
			put("/statistics",stringEncode("Statistiques"));
			put("/rss",stringEncode("Widget Rss"));
			put("/bookmark",stringEncode("Widget Signets"));

			put("/xiti",stringEncode("Xiti"));*/
		}
	};

    @Override
	public void start() throws Exception {
		super.start();
		ConfigManager.init(config);
		/*
		if (!Config.getInstance().checkConfiguration()) {
			LocalMap<String, String> deploymentsIdMap = vertx.sharedData().getLocalMap("deploymentsId");
			vertx.undeploy(deploymentsIdMap.get("fr.openent.supportpivot"));
			return;
	   */

		ServiceManager.init(vertx, config);
		addController(new SupportController());
		addController(new JiraController());
		addController(new LdeController());

	}

}
