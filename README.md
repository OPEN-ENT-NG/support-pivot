# À propos de l'application SupportPivot

* Licence : [AGPL v3](http://www.gnu.org/licenses/agpl.txt) - Copyright Ville de Paris
* Développeur(s) : CGI
* Financeur(s) : Région Ville de Paris
* Description : Application permettant l'automatisation de l'échange de tickets support entre l'application support de l'ENT, LDE, et JIRA. 

## Déployer dans ent-core
<pre>
		build.sh clean install
</pre>

# Présentation du module

Ce module permet l'échange de tickets entre différents outils de ticketing.

Les outils supportés sont :
* le module support de l'ENT ( mini v1.1)
* L'outil LDE
* JIRA d'Attlassian

Flux de création autorisés :
  ENT -> JIRA
  LDE -> JIRA   : interdit
  JIRA -> *    : interdit
  
Flux de Mise à jour autorisés :
  ENT -> JIRA
  LDE -> JIRA
  JIRA -> ENT
           


## Fonctionnalités

Le module expose 
           sur le BUS une interface permettant d'escalader un ticket vers JIRA. (Utiliser par support de l'ENT)
           une route permettant à LDE d'escalader des tickets vers JIRA.
           une route permettant à JIRA d'escalader un ticket vers le mosule support de l'ENT (maj uniquement)

Le module exploite 
           sur le BUS l'interface du module Support pour escalader les mise à jour provenant JIRA

## Configuration

Liste des modules à associer à son nom dans Jira et ajouter "Pas de composant associé dans Jira" comme composant dans Jira :

    "/actualites" : ${supportPivotJiraActualites},
    "/calendar" : ${supportPivotJiraCalendar},
    "/support" : ${supportPivotJiraSupport},
    "/userbook/annuaire#/search" : ${supportPivotJiraAnnuaire},
    "/community" : ${supportPivotJiraCommunity},
    "/autres" : ${supportPivotJiraAutres},
    "/blog" : ${supportPivotJiraBlog},
    "/mindmap" : ${supportPivotJiraMindmap},
    "/rack": ${supportPivotJiraRack},
    "/workspace/workspace" : ${supportPivotJiraWorkspace},
    "/exercizer" : ${supportPivotJiraExercizer},
    "/formulaire" : ${supportPivotJiraFormulaire},
    "/forum" : ${supportPivotJiraForum},
    "/timelinegenerator" : ${supportPivotJiraTimeline},
    "/lool" : ${supportPivotJiraLool},
    "/conversation/conversation" : ${supportPivotJiraConversation},
    "/moodle" : ${supportPivotJiraMoodle},
    "/collaborativewall" : ${supportPivotJiraCollaborativeWall},
    "/mediacentre" : ${supportPivotJiraMediacentre},
    "/collaborativeeditor" : ${supportPivotJiraCollaborativeEditor},
    "/pages" : ${supportPivotJiraPages},
    "/peertube" : ${supportPivotJiraPeertube},
    "/rbs" : ${supportPivotJiraRbs},
    "/schooltoring" : ${supportPivotJiraSchooltoring},
    "/site-web" : ${supportPivotJiraSiteweb},
    "/poll": ${supportPivotJiraPoll},
    "/statistics" : ${supportPivotJiraStatistics},
    "/telechargement" : ${supportPivotJiraTelechargement},
    "web-conference" : ${supportPivotJiraWebconf},
    "/wiki" : ${supportPivotJiraWiki},
    "/application-mobile" : ${supportPivotJiraApplicationMobile},
    "/cas" : ${supportPivotJiraCas},
    "/auth/saml/sso/urn:gar:ihmAffectation:seclin" : ${supportPivotJiraGar},
    "/portail" : ${supportPivotJiraPortail},
    "/trousseau" : ${supportPivotJiraTrousseau},
    "/vie-scolaire" : ${supportPivotJiraViescolaire},
    "/homework-assistance" : ${supportPivotJiraHomework},,
    "/pronote" : ${supportPivotJiraPronote},
    "/rss" : ${supportPivotJiraRss},
    "/bookmark" : ${supportPivotJiraBookmark},
    "/xiti" : ${supportPivotJiraXiti},
    "/minibadge" : ${supportPivotJiraMinibadge},
    "/crre" : ${supportPivotJiraCrre},
    "/scrapbook" : ${supportPivotJiraScrapbook},
    "/admin" : ${supportPivotJiraAdmin},
    "/directory/admin-console" : ${supportPivotJiraAdminConsole},
    "lystore" : ${supportPivotJiraLystore}


Contenu du fichier deployment/support/conf.json.template :

     {
          "name": "fr.openent~supportpivot~1.0.1",
          "config": {
            "main" : "fr.openent.supportpivot.Supportpivot",
            "port" : 9595,
            "app-name" : "Supportpivot",
        	"app-address" : "/supportpivot",
        	"app-icon" : "Supportpivot-large",
            "host": "${host}",
            "ssl" : $ssl,
            "auto-redeploy": false,
            "userbook-host": "${host}",
            "app-registry.port" : 8012,
            "mode" : "${mode}",
            "entcore.port" : 8009,
    		"collectivity" : "${supportPivotCollectivity}",
    		"academy" : "${supportPivotAcademy}",
    		"default-attribution" : "${supportPivotDefAttribution}",
    		"default-tickettype" : "${supportPivotDefTicketType}",
    		"default-priority" : "${supportPivotDefTicketPriority}",
            "jira-login" : "${supportPivotJIRALogin}",
            "jira-passwd" : "${supportPivotJIRAPwd}",
            "jira-host" : "${supportPivotJIRAHost}",
            "jira-url" : "/rest/api/2/issue/",
            "jira-project-key" :  "${supportPivotJIRAProjectKey}",
            "jira-allowed-tickettype" :  "${supportPivotJIRAAllowedTicketType}",
            "jira-allowed-priority":  "${supportPivotJIRAAllowedPriority}",
            "jira-custom-fields" : {
                "id_ent" : "${supportPivotCFIdEnt}",
                "id_externe" : "${supportPivotCFIdExterne}",
                "status_ent" : "${supportPivotCFStEnt}",
                "status_externe" : "${supportPivotCFStExterne}",
                "creation" : "${supportPivotCFCreateDate}",
                "resolution_ent" : "${supportPivotCFResEnt}",
                "creator" : "${supportPivotCFCreator}",
                "response_technical" : "${supportPivotCFTechResp}",
                "uai" : "${supportPivotCFUai}"
            },
        	"jira-status-mapping": {
        		"statutsJira": ${supportPivotMappingStatus},
        		"statutsDefault" : "${supportPivotDefaultStatus}"
        	}
          }
        }


Les paramètres spécifiques à l'application support sont les suivants :

    mod parameter           :  conf.properties variable         ,   usage
    -------------------------------------------------------------------------------------------------------------------
    -------------------------------------------------------------------------------------------------------------------
    "collectivity"          : "${supportPivotCollectivity}"  , collectivity name 
    "academy"               : "${supportPivotAcademy}"  , academy name
    "default-attribution"   : "${supportPivotDefAttribution}"   , default attribution among ENT, RECTORAT, CGI
    "default-tickettype"    : "${supportPivotDefTicketType}"    , default ticket type
    "default-priority"      : "${supportPivotDefTicketPriority}", default ticket priority
    -------------------------------------------------------------------------------------------------------------------
                JIRA escalating
    -------------------------------------------------------------------------------------------------------------------
    "jira-login"                : "${supportPivotJIRALogin"                 , JIRA login 
    "jira-passwd"               : "${supportPivotJIRAPwd}"                  , JIRA password
    "jira-host"                 : "${supportPivotJIRAHost}"                 , JIRA host  ex: http://mysite.com:8080/jira
    "jira-project-key"          :  "${supportPivotJIRAProjectKey}"          , JIRA key of dest project
    "jira-allowed-tickettype"   :  "${supportPivotJIRAAllowedTicketType}"   , JIRA ticket type i.e [bug, task] 
    "jira-allowed-priority"     :  "${supportPivotJIRAAllowedPriority}"     , Order 3 priorities [low, mid, high]
    -------------------------------------------------------------------------------------------------------------------
                JIRA Custom fields used to display informations
                All theses customs fields have to be defined in JIRA for the screens 
    -------------------------------------------------------------------------------------------------------------------
    "id_ent"                : "${supportPivotCFIdEnt}"          , Jira field id for ENT id of the ticket
    "id_externe"            : "${supportPivotCFIdExterne}"      , Jira field id for external id of the ticket
    "status_ent"            : "${supportPivotCFStEnt}"          , Jira field id for ENT status of the ticket
    "status_externe"        : "${supportPivotCFStExterne}"      , Jira field id for external id of the ticket
    "creation"              : "${supportPivotCFCreateDate}"     , Jira field id for creation date
    "resolution_ent"        : "${supportPivotCFResEnt}"         , Jira field id for ENT resolution date
    "creator"               : "${supportPivotCFCreator}"        , Jira field id for creator of the ticket
    "response_technical"    : "${supportPivotCFTechResp}"       , Jira field id for CGI technical response
    "statutsJira"           : ${supportPivotMappingStatus}      , Status Mapping table. give for each JIRA status a 
                                                                  corresponding expected status
                                                                   ex. [ "Nouveau": ["JiraStatus1"],
                                                                         "Ouvert": ["JiraStatus2","JiraStatus3"],
                                                                         "Résolu": ["JiraStatus4"],
                                                                         "Fermé": ["JiraStatus5"]   ]             
    "statutsDefault"        : "${supportPivotDefaultStatut}"                 , Default statut sent if no corresponding found 
                                                                   in mapping table above
