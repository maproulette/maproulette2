toastr.options.positionClass = "notification-position";

// Globals

// Max width of marker balloon text
var MAX_BALLOON_TEXT_WIDTH=40;

L.TileLayer.Common = L.TileLayer.extend({
    initialize: function (options) {
        L.TileLayer.prototype.initialize.call(this, this.url, options);
    },
    // Workaround until https://github.com/Leaflet/Leaflet/issues/4915 is released
    options: { maxZoom: 19 }
});

// -- CUSTOM CONTROLS ----------------------------------
// Help control, when you click on this the help screen will overlay the map
L.Control.Help = L.Control.extend({
    options: {
        position: 'topright',
        text: "Help Text"
    },
    onAdd: function(map) {
        var container = L.DomUtil.create('div', 'mp-control mp-control-component');
        var control = L.DomUtil.create('a', 'fa fa-question fa-2x', container);
        control.href = "#";
        var text = L.DomUtil.create('span', '', container);
        text.innerHTML = " Help";
        var self = this;
        L.DomEvent.on(container, 'click', L.DomEvent.stopPropagation)
            .on(container, 'click', L.DomEvent.preventDefault)
            .on(container, 'click', function (e) {
                $("#infoText").innerHTML = marked(self.options.text);
                $("#information").modal({backdrop:false});
            });
        return container;
    }
});

// Survey Questionaire control
L.Control.SurveyControl = L.Control.extend({
    options: {
       position: 'bottomright'
    },
    onAdd: function(map) {
        var container = L.DomUtil.create('div', 'mp-survey-control hidden');
        container.id = "survey_container";
        var questionP = L.DomUtil.create('p', 'mp-control-component', container);
        questionP.id = "survey_question";
        var answerDiv = L.DomUtil.create('div', 'mp-control-component', container);
        answerDiv.id = "survey_answers";
        return container;
    },
    updateSurvey: function(question, answers) {
        L.DomUtil.get("survey_question").innerHTML = question;
        var answerDiv = L.DomUtil.get("survey_answers");
        answerDiv.innerHTML = "";
        for (var i = 0; i < answers.length; i++) {
            this.buildAnswer(answerDiv, answers[i].id, answers[i].answer);
        }
    },
    buildAnswer: function(answerDiv, id, answer) {
        var answerButton = L.DomUtil.create('button', 'btn-xs btn-block btn-default', answerDiv);
        answerButton.innerHTML = answer;
        L.DomEvent.on(answerButton, 'click', L.DomEvent.stopPropagation)
            .on(answerButton, 'click', L.DomEvent.preventDefault)
            .on(answerButton, 'click', function() {
                MRManager.answerSurveyQuestion(id);
            });
    },
    hide: function() {
        L.DomUtil.addClass(L.DomUtil.get("survey_container"), "hidden");
    },
    show: function() {
        L.DomUtil.removeClass(L.DomUtil.get("survey_container"), "hidden");
    }
});

// Edit Control - When user clicks on edit this will show up in the center of the screen
L.Control.EditControl = L.Control.extend({
    options: {
       position: 'bottomright'
    },
    onAdd: function(map) {
        var container = L.DomUtil.create('div', 'mp-edit-control hidden');
        container.id = "edit_container";
        var information = L.DomUtil.create('p', 'mp-control-component', container);
        information.id = "edit_information";
        var options = L.DomUtil.create('div', 'mp-control-component', container);
        options.id = "edit_options";
        return container;
    },
    setAsEdit: function() {
        var self = this;
        L.DomUtil.get("edit_information").innerHTML = Messages("mapping.js.control.edit.information");
        var options = L.DomUtil.get("edit_options");
        options.innerHTML = "";
        var editInID = L.DomUtil.create('button', 'btn-xs btn-block btn-default', options);
        editInID.innerHTML = Messages("mapping.js.control.edit.id");
        L.DomEvent.on(editInID, 'click', L.DomEvent.stopPropagation)
            .on(editInID, 'click', L.DomEvent.preventDefault)
            .on(editInID, 'click',  MRManager.openTaskInId);

        var editInJOSM = L.DomUtil.create('button', 'btn-xs btn-block btn-default', options);
        editInJOSM.innerHTML = Messages("mapping.js.control.edit.josm");
        L.DomEvent.on(editInJOSM, 'click', L.DomEvent.stopPropagation)
            .on(editInJOSM, 'click', L.DomEvent.preventDefault)
            .on(editInJOSM, 'click', function() {
                MRManager.openTaskInJosm(false);
            });

        var editInJOSMLayer = L.DomUtil.create('button', 'btn-xs btn-block btn-default', options);
        editInJOSMLayer.innerHTML = Messages("mapping.js.control.edit.josm.layer");
        L.DomEvent.on(editInJOSMLayer, 'click', L.DomEvent.stopPropagation)
            .on(editInJOSMLayer, 'click', L.DomEvent.preventDefault)
            .on(editInJOSMLayer, 'click', function() {
                MRManager.openTaskInJosm(true);
            });

        var closeEdit = L.DomUtil.create('button', 'btn-xs btn-block btn-default', options);
        closeEdit.innerHTML = Messages("mapping.js.control.edit.nevermind");
        L.DomEvent.on(closeEdit, 'click', L.DomEvent.stopPropagation)
            .on(closeEdit, 'click', L.DomEvent.preventDefault)
            .on(closeEdit, 'click', function() {
                self.hide();
            });
    },
    setAsResult: function() {
        L.DomUtil.get("edit_information").innerHTML = Messages("mapping.js.control.results.title");
        var options = L.DomUtil.get("edit_options");
        options.innerHTML = "";
        var fixed = L.DomUtil.create('button', 'btn-xs btn-block btn-default', options);
        fixed.innerHTML = Messages("mapping.js.control.results.fixed");
        L.DomEvent.on(fixed, 'click', L.DomEvent.stopPropagation)
            .on(fixed, 'click', L.DomEvent.preventDefault)
            .on(fixed, 'click', function() {
                MRManager.setTaskStatus(TaskStatus.FIXED);
            });

        var difficult = L.DomUtil.create('button', 'btn-xs btn-block btn-default', options);
        difficult.innerHTML = Messages("mapping.js.control.results.difficult");
        L.DomEvent.on(difficult, 'click', L.DomEvent.stopPropagation)
            .on(difficult, 'click', L.DomEvent.preventDefault)
            .on(difficult, 'click', function() {
                MRManager.setTaskStatus(TaskStatus.TOOHARD);
            });

        var alreadyFixed = L.DomUtil.create('button', 'btn-xs btn-block btn-default', options);
        alreadyFixed.innerHTML = Messages("mapping.js.control.results.alreadyfixed");
        L.DomEvent.on(alreadyFixed, 'click', L.DomEvent.stopPropagation)
            .on(alreadyFixed, 'click', L.DomEvent.preventDefault)
            .on(alreadyFixed, 'click', function() {
                MRManager.setTaskStatus(TaskStatus.ALREADYFIXED);
            });
    },
    hide: function() {
        MRManager.updateMRControls();
        L.DomUtil.addClass(L.DomUtil.get("edit_container"), "hidden");
    },
    show: function() {
        MRManager.updateMRControls();
        L.DomUtil.removeClass(L.DomUtil.get("edit_container"), "hidden");
    }
});

// Control panel for all the task functions
L.Control.ControlPanel = L.Control.extend({
    options: {
        position: 'bottomright',
        controls:[false, false, false, false],
        showText:true,
        signedIn:false,
        editClick:function() {}
    },
    onAdd: function(map) {
        // we create all the containers first so that the ordering will always be consistent
        // no matter what controls a user decides to put on the map
        var container = L.DomUtil.create('div', 'mp-control');
        var prevDiv = L.DomUtil.create('div', 'mp-control-component pull-left', container);
        prevDiv.id = "controlpanel_previous";
        var editDiv = L.DomUtil.create('div', 'mp-control-component pull-left', container);
        editDiv.id = "controlpanel_edit";
        var fpDiv = L.DomUtil.create('div', 'mp-control-component pull-left', container);
        fpDiv.id = "controlpanel_fp";
        var nextDiv = L.DomUtil.create('div', 'mp-control-component pull-left', container);
        nextDiv.id = "controlpanel_next";
        return container;
    },
    // updates the controls, removes and adds if necessary
    update: function(signedIn, prevControl, editControl, fpControl, nextControl) {
        this.options.signedIn = signedIn;
        this.options.controls[0] = prevControl;
        this.options.controls[1] = editControl;
        this.options.controls[2] = fpControl;
        this.options.controls[3] = nextControl;
        this.updateControls();
    },
    // generic function to update the controls on the map
    updateControl: function(controlID, controlName, friendlyName, icon, locked, clickHandler) {
        if (this.options.controls[controlID]) {
            var controlDiv = L.DomUtil.get(controlName);
            if (!controlDiv.hasChildNodes()) {
                var control = L.DomUtil.create('a', 'fa ' + icon + ' fa-lg', controlDiv);
                control.href = "#";
                var text = L.DomUtil.create('a', '', controlDiv);
                text.href = "#";
                if (this.options.showText) {
                    text.innerHTML = " " + friendlyName;
                }
                L.DomEvent.on(controlDiv, 'click', L.DomEvent.stopPropagation)
                    .on(controlDiv, 'click', L.DomEvent.preventDefault)
                    .on(controlDiv, 'click', clickHandler);
            }
            if (locked) {
                L.DomUtil.addClass(controlDiv, "mp-control-component-locked");
            } else {
                L.DomUtil.removeClass(controlDiv, "mp-control-component-locked");
            }
        } else {
            $("#" + controlName).innerHTML = "";
        }

    },
    updateControls: function() {
        this.updatePreviousControl();
        this.updateEditControl();
        this.updateFPControl();
        this.updateNextControl();
    },
    updatePreviousControl: function() {
        this.updateControl(0, "controlpanel_previous", Messages("mapping.js.control.previous"), "fa-backward", false, function(e) {
            MRManager.getPreviousTask();
        });
    },
    updateEditControl: function() {
        var self = this;
        var locked = MRManager.isTaskLocked() || MRManager.isChallengeSurvey();
        this.updateControl(1, "controlpanel_edit", Messages("mapping.js.control.edit"), "fa-pencil",
            locked || !this.options.signedIn, function(e) {
            if (!locked) {
                self.options.editClick();
            }
        });
    },
    updateFPControl: function() {
        var locked = MRManager.isTaskLocked() || MRManager.isChallengeSurvey();
        this.updateControl(2, "controlpanel_fp", Messages("mapping.js.control.falsepositive"), "fa-warning",
            locked || !this.options.signedIn, function(e) {
            if (!locked) {
                MRManager.setTaskStatus(TaskStatus.FALSEPOSITIVE);
            }
        });
    },
    updateNextControl: function() {
        var nextName = Messages("mapping.js.control.skip");
        // this checks to see if the previous button is being shown, if it is then we know that
        // we are in debug mode and makes sense to call the button "Next" instead of "Skip"
        if (this.options.controls[0]) {
            nextName = Messages("mapping.js.control.next");
        }
        this.updateControl(3, "controlpanel_next", nextName, "fa-forward", false, function(e) {
            MRManager.getNextTask(MRManager.isTaskLocked());
        });
    },
    disableControls: function() {
        L.DomUtil.addClass(L.DomUtil.get("controlpanel_edit"), "mp-control-component-locked");
        L.DomUtil.addClass(L.DomUtil.get("controlpanel_fp"), "mp-control-component-locked");
    },
    enableControls: function() {
        L.DomUtil.removeClass(L.DomUtil.get("controlpanel_edit"), "mp-control-component-locked");
        L.DomUtil.removeClass(L.DomUtil.get("controlpanel_fp"), "mp-control-component-locked");
    }
});
// -----------------------------------------------------

// add various basemap layers to the TileLayer namespace
(function () {
    var osmAttr = '&copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors, <a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>';

    L.TileLayer.OpenStreetMap = L.TileLayer.Common.extend({
        url: 'http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
        options: {attribution: osmAttr}
    });

    L.TileLayer.OpenCycleMap = L.TileLayer.Common.extend({
        url: 'http://{s}.tile.opencyclemap.org/cycle/{z}/{x}/{y}.png',
        options: {
            attribution: '&copy; OpenCycleMap, ' + 'Map data ' + osmAttr
        }
    });

    L.TileLayer.Bing = L.TileLayer.Common.extend({
        url: 'http://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
        options: {
            attribution: 'Tiles &copy; Esri &mdash; Source: Esri, i-cubed, USDA, USGS, AEX, GeoEye, Getmapping, Aerogrid, IGN, IGP, UPR-EGP, and the GIS User Community'
        }
    });
}());

// A simple point class
function Point(x, y) {
    this.x = x;
    this.y = y;
}

var TaskStatus = {
    CREATED:0,
    FIXED:1,
    FALSEPOSITIVE:2,
    SKIPPED:3,
    DELETED:4,
    ALREADYFIXED:5,
    TOOHARD:6,
    getStatusName:function(status) {
        switch(status) {
            case TaskStatus.CREATED: return Messages("mapping.js.task.created");
            case TaskStatus.FIXED: return Messages("mapping.js.task.fixed");
            case TaskStatus.FALSEPOSITIVE: return Messages("mapping.js.task.falsepositive");
            case TaskStatus.SKIPPED: return Messages("mapping.js.task.skipped");
            case TaskStatus.DELETED: return Messages("mapping.js.task.deleted");
            case TaskStatus.ALREADYFIXED: return Messages("mapping.js.task.alreadyfixed");
            case TaskStatus.TOOHARD: return Messages("mapping.js.task.toohard");
            default: return Messages("mapping.js.task.unknown");
        }
    },
    isAvailableToEdit:function(status) {
        return status == TaskStatus.CREATED || status == TaskStatus.SKIPPED || status == TaskStatus.TOOHARD;
    }
};

// The available editors that can be defaulted by the user
var Editors = {
    NONE:-1,
    ID:0,
    JOSM:1,
    JOSMLAYERS:2
};

// The available basemaps users can default the map too
var Basemaps = {
    NONE:-1,
    OSM:0,
    OCM:1,
    BING:2,
    CUSTOM:3
};

/**
 * Challenge class to handle anything related to challenges
 *
 * @constructor
 */
function Challenge() {
    var data = {id:-1};
    this.getData = function() {
        return data;
    };

    this.resetChallenge = function() {
        data = {id:-1};
        MRManager.updateMapOptions("map", new Point(0, 0));
    };
    
    this.resetTask = function() {
        this.data = {id:-1};
    };

    this.isSurvey = function() {
        return data.challengeType == 4;
    };

    /**
     * Updates this object to the challenge with the provided ID
     *
     * @param challengeId ID of the new challenge
     * @param success handler for successful results
     * @param error handler for failed results
     */
    this.updateChallenge = function(challengeId, success, error) {
        if (data.id != challengeId) {
            jsRoutes.org.maproulette.controllers.api.ChallengeController.getChallenge(challengeId).ajax({
                success: function (update) {
                    if (typeof update.challenge === 'undefined') {
                        data = update;
                        // remove the survey panel from the map
                    } else {
                        data = update.challenge;
                        data.answers = update.answers;
                    }

                    // update with any map options
                    MRManager.updateMapOptions("map", new Point(0, 0), {
                        defaultZoom: Utils.getDefaultValue(data.defaultZoom, 13),
                        minZoom: Utils.getDefaultValue(data.minZoom, 3),
                        maxZoom: Utils.getDefaultValue(data.maxZoom, 19),
                        layer: Utils.getDefaultValue(data.defaultBasemap, Basemaps.NONE),
                        customLayerURI: Utils.getDefaultValue(data.customBasemap, "")
                    });
                    // if it is a survey we need to add the survey control panel to the map
                    MRManager.updateMRControls();
                    if (typeof success != 'undefined') {
                        success();
                    }
                },
                error: function () {
                    if (typeof error != 'undefined') {
                        error();
                    }
                }
            });
        }
    };

    /**
     * Answers a survey question, will do nothing if the current challenge is not a survey
     *
     * @param taskId ID of the task for the answer
     * @param answerId The id for the answer
     * @param success success handler
     * @param error error handler
     */
    this.answerQuestion = function(taskId, answerId, success, error) {
        // See Actions.scala which contains ID's for items. 4 = Survey
        if (this.isSurvey()) {
            ToastUtils.Success(Messages("mapping.js.task.answered") + " [" + data.instruction + "]");
            MRManager.loading();
            jsRoutes.org.maproulette.controllers.api.SurveyController.answerSurveyQuestion(data.id, taskId, answerId).ajax({
                success: MRManager.getSuccessHandler(function() {
                    if (typeof success === 'undefined') {
                        MRManager.getNextTask();
                    } else {
                        success();
                    }
                }),
                error: MRManager.getErrorHandler(error)
            });
        }
    };
    
    this.view = function(challengeId, filters, success, error) {
        MRManager.loading();
        jsRoutes.org.maproulette.controllers.api.ChallengeController.getClusteredPoints(challengeId, filters).ajax({
            success: MRManager.getSuccessHandler(function(data) {
                if (typeof success === 'undefined') {
                    MRManager.viewClusteredData(data);
                } else {
                    success();
                }
            }),
            error: MRManager.getErrorHandler(error)
        });
    };
}

/**
 * The Task class contains all relevent information regarding the current task loaded on the map.
 * Also contains functionality to get next, previous and random tasks.
 *
 * @param data This is the task data that would be in the form
 * {
 *  "id":<TASK_ID>,
 *  "parentId":<CHALLENGE_ID>,
 *  "name":"<NAME>",
 *  "instruction":"<INSTRUCTION>",
 *  "status":"<STATUS>",
 *  "geometry":"<GEOJSON>"
 *  }
 * @constructor
 */
function Task() {
    var challenge = new Challenge();
    this.getChallenge = function() {
        return challenge;
    };
    var data = {id:-1, parentId:-2};
    this.getData = function() {
        return data;
    };
    this.setSeedData = function(parentId, taskId) {
        if (typeof parentId !== 'undefined') {
            data.parentId = parentId;
        }
        if (typeof taskId !== 'undefined') {
            data.id = taskId;
        }
    };

    var updateData = function(update, success) {
        MRManager.loading();
        data = update;
        if (challenge.getData().id != data.parentId) {
            challenge.updateChallenge(data.parentId, success);
        } else {
            success();
        }
    };

    this.resetTask = function() {
        data = {id:-1, parentId:-1};
    };

    this.isLocked = function() {
        return data.locked || data.status == TaskStatus.FIXED ||
            data.status == TaskStatus.FALSEPOSITIVE || data.status == TaskStatus.DELETED ||
            data.status == TaskStatus.ALREADYFIXED;
    };

    this.updateTask = function(taskId, success, error) {
        jsRoutes.controllers.MappingController.getTaskDisplayGeoJSON(taskId).ajax({
            success:function(update) {
                updateData(update, MRManager.getSuccessHandler(success));
            },
            error:MRManager.getErrorHandler(error)
        });
    };

    this.getNextTask = function(success, error) {
        if (data.parentId == -1 && data.id == -1) {
            ToastUtils.Info(Messages("mapping.js.task.debugmode"));
        } else {
            jsRoutes.controllers.MappingController
                .getSequentialNextTask(data.parentId, data.id)
                .ajax({
                    success: function (update) {
                        updateData(update, MRManager.getSuccessHandler(success));
                    },
                    error: MRManager.getErrorHandler(error)
                });
        }
    };

    this.getPreviousTask = function(success, error) {
        if (data.parentId == -1 && data.id == -1) {
            ToastUtils.Info(Messages("mapping.js.task.debugmode"));
        } else {
            jsRoutes.controllers.MappingController
                .getSequentialPreviousTask(data.parentId, data.id)
                .ajax({
                    success: function (update) {
                        updateData(update, MRManager.getSuccessHandler(success));
                    },
                    error: MRManager.getErrorHandler(error)
                });
        }
    };

    this.getRandomNextTask = function(success, error) {
        var taskFunction = jsRoutes.controllers.MappingController.getRandomNextTask;
        if (MRManager.usingPriority()) {
            taskFunction = jsRoutes.controllers.MappingController.getRandomNextTaskWithPriority;
        }
        taskFunction().ajax({
            success:function(update) {
                updateData(update, MRManager.getSuccessHandler(success));
            },
            error:MRManager.getErrorHandler(error)
        });
    };
    
    this.setTaskStatus = function(status, success, error) {
        var self = this;
        var errorHandler = MRManager.getErrorHandler(error);
        var statusSetSuccess = function () {
            ToastUtils.Success(Messages("mapping.js.task.status.result", data.name, TaskStatus.getStatusName(status)));
            self.getRandomNextTask(MRManager.getSuccessHandler(success), errorHandler);
        };
        jsRoutes.org.maproulette.controllers.api.TaskController.setTaskStatus(data.id, status)
            .ajax({success: statusSetSuccess, error: errorHandler});
    };
}

var MRManager = (function() {
    var map;
    var markers = L.markerClusterGroup();
    var currentGeoJSON = "";
    var geojsonLayer;
    var layerControl;
    var currentTask = new Task();
    // controls
    var controlPanel = new L.Control.ControlPanel({
        editClick:function() {
            if (LoggedInUser.defaultEditor === Editors.ID) {
                openTaskInId();
            } else if (LoggedInUser.defaultEditor === Editors.JOSM) {
                openTaskInJosm(false);
            } else if (LoggedInUser.defaultEditor === Editors.JOSMLAYERS) {
                openTaskInJosm(true);
            } else {
                editPanel.setAsEdit();
                editPanel.show();
                controlPanel.disableControls();
            }
        }
    });
    var surveyPanel = new L.Control.SurveyControl({});
    var editPanel = new L.Control.EditControl({});
    // In debug mode tasks will not be edited and the previous button is displayed in the control panel
    var debugMode = Boolean(Utils.getQSParameterByName("debug"));
    var currentSearchParameters = new SearchParameters();
    var signedIn = false;
    var disableKeys = true;

    // Function that handles the resizing of the map when the menu is toggled
    var resizeMap = function() {
        var mapDiv = $("#map");
        var notifications = $(".notification-position");
        var menuOpenNotifications = $(".notification-position-menuopen");
        var sidebarWidth = $("#sidebar").width();
        if (sidebarWidth == 50) {
            mapDiv.animate({left: '230px'});
            notifications.animate({left: '270px'});
            menuOpenNotifications.animate({left: '270px'});
        } else if (sidebarWidth == 230) {
            mapDiv.animate({left: '50px'});
            notifications.animate({left: '90px'});
            menuOpenNotifications.animate({left: '90px'});
        }
    };

    var init = function (userSignedIn, element, point) {
        updateMapOptions(element, point);

        // handles click events that are executed when submitting the custom geojson from the geojson viewer
        $('#geojson_submit').on('click', function() {
            disableKeys = false;
            if ($('#geojson_text').val().length < 1) {
                $('#geoJsonViewer').modal("hide");
                return;
            }
            viewGeoJsonData(JSON.parse($('#geojson_text').val()));
            $('#geoJsonViewer').modal("hide");
        });
        // handles the click event from the sidebar toggle
        $("#sidebar_toggle").on("click", resizeMap);
        signedIn = userSignedIn;
        //register the keyboard shortcuts
        $('#searchQ').on('focus', function() {
            searchInFocus = true;
        });
        $('#searchQ').on('focusout', function() {
            searchInFocus = false;
        });
        registerHotKeys();
    };

    var updateMapOptions = function(mapElement, point, options) {
        if (typeof options === 'undefined') {
            options = {layer: LoggedInUser.defaultBasemap, customLayerURI: LoggedInUser.customBasemap};
        } else {
            if (Utils.getDefaultValue(options.layer, Basemaps.NONE) === Basemaps.NONE) {
                options.layer = LoggedInUser.defaultBasemap;
            }
            if (Utils.getDefaultValue(options.customLayerURI, "") === "") {
                options.customLayerURI = LoggedInUser.customBasemap;
            }
        }
        var osm_layer = new L.TileLayer.OpenStreetMap();
        var layers = Utils.getDefaultValue(options.layers, {
            'OSM': osm_layer,
            'OpenCycleMap': new L.TileLayer.OpenCycleMap(),
            'Bing Aerial': new L.TileLayer.Bing()
        });
        var mapLayer = Utils.getDefaultValue(options.layer, Basemaps.OSM);
        var currentLayer = layers.OSM;
        if (typeof mapLayer === 'number') {
            if (mapLayer === Basemaps.OCM) {
                currentLayer = layers.OpenCycleMap;
            } else if (mapLayer === Basemaps.BING) {
                currentLayer = layers['Bing Aerial'];
            } else if (mapLayer === Basemaps.CUSTOM && options.customLayerURI !== "") {
                L.TileLayer.Custom = L.TileLayer.Common.extend({
                    url: options.customLayerURI,
                    options: {
                        attribution: Messages("mapping.js.custom.attribution")
                    }
                });
                layers.Custom = new L.TileLayer.Custom();
                currentLayer = layers.Custom;
            } else {
                currentLayer = layers.OSM;
            }
        } else {
            currentLayer = mapLayer;
        }

        // we have to completely remove the map div because the map could be partially
        // initialized and will throw an exception if we try to initialize it again.
        var parent = $("#" + mapElement).parent();
        $("#" + mapElement).remove();
        parent.append('<div id="' + mapElement + '" onclick="hideSidebar();"></div>');
        try {
            map = new L.Map(mapElement, {
                center: new L.LatLng(point.x, point.y),
                zoom: Utils.getDefaultValue(options.defaultZoom, 13),
                minZoom: Utils.getDefaultValue(options.minZoom, 3),
                maxZoom: Utils.getDefaultValue(options.maxZoom, 19),
                layers: [
                    currentLayer
                ],
                zoomControl: false
            });
        } catch (err) {
            // lets assume that loading the map was caused by the custom url
            if (typeof layers.Custom !== 'undefined') {
                map = new L.Map(mapElement, {
                    center: new L.LatLng(point.x, point.y),
                    zoom: Utils.getDefaultValue(options.defaultZoom, 13),
                    minZoom: Utils.getDefaultValue(options.minZoom, 3),
                    maxZoom: Utils.getDefaultValue(options.maxZoom, 19),
                    layers: [
                        osm_layer
                    ],
                    zoomControl: false
                });
                ToastUtils.Error(Messages("mapping.js.custom.error", LoggedInUser.customBasemap));
            } else {
                ToastUtils.Error(err);
            }
        }

        L.control.zoom({
            position:'topright'
        }).addTo(map);

        // geojson layer
        geojsonLayer = new L.GeoJSON(null, {
            onEachFeature: function (feature, layer) {
                if (feature.properties) {
                    var counter = 0;
                    var popupString = '<div class="popup">';
                    for (var k in feature.properties) {
                        counter++;
                        var v = feature.properties[k];
                        var printLine = k+": "+v;
                        if(printLine.length > MAX_BALLOON_TEXT_WIDTH){
                            popupString += printLine.slice(0,(MAX_BALLOON_TEXT_WIDTH - 2))+".."+ '<br />';
                        } else{
                            popupString += printLine+ '<br />';
                        }
                    }
                    popupString += '</div>';
                    if (counter > 0) {
                        layer.bindPopup(popupString, {
                            maxHeight: 250
                        });
                    }
                }
            }
        });
        map.addLayer(geojsonLayer);
        // cluster marker layer
        map.addLayer(markers);
        var overlays = {'GeoJSON': geojsonLayer};
        layerControl = L.control.layers(layers, overlays, {position:"topright"});

        //map.addControl(new L.Control.Help({}));
        map.addControl(layerControl);
        map.addControl(controlPanel);
        map.addControl(surveyPanel);
        map.addControl(editPanel);
        map._onMoveEnd();
        $("#map").css("left", $("#sidebar").width());
    };

    // Displays the geojson data on the map
    var viewGeoJsonData = function(data) {
        currentGeoJSON = data;
        geojsonLayer.addData(currentGeoJSON);
        map.fitBounds(geojsonLayer.getBounds());
        // in this scenario the task needs to be reset
        currentTask.resetTask();
        window.history.pushState("", "", "");
        controlPanel.update(signedIn, debugMode, false, false, false);
    };

    // Displays cluster address points on the map
    var viewClusteredData = function(data) {
        currentTask.getChallenge().resetChallenge();
        currentGeoJSON = {};
        var popupFunction = function(id) {
            return function(event) {
                if ($("#statusPieChart_" + id).is(':empty')) {
                    Metrics.getChallengeSummaryPieChart($("#statusPieChart_" + id), id, false);
                }
            };
        };

        if (data.length > 0) {
            for (var i = 0; i < data.length; i++) {
                var title = data[i].title;
                var marker = L.marker(new L.LatLng(data[i].point.lat, data[i].point.lng), {title:title});
                var popupString = '<div class="popup mp-popup" id="popup_' + data[i].id + '">';
                if (data[i].type == 4) {
                    popupString = '<i class="fa fa-edit"/>';
                } else if (data[i].type == 1) {
                    popupString = '<i class="fa fa-wrench "/>';
                }
                if (title !== "") {
                    popupString += "<b>" + title + "</b></br>";
                }
                if (data[i].type == 1 || data[i].type == 4) {
                    // This section below is for the pie chart and small activity chart when the popup is opened
                    popupString += Messages('challenge.difficulty.title') + " " + Messages('challenge.difficulty.' + data[i].difficulty);
                    if (typeof data[i].ownerName !== 'undefined' || data[i].ownerName == -1) {
                        popupString += "<br/>" + Messages('challenge.owner') + " <a target='_blank' href='http://osm.org/user/" + data[i].ownerName + "'>" + data[i].ownerName + "</a>" +
                                        "<a target='_blank' href='https://www.openstreetmap.org/message/new/" + data[i].ownerName + "'> <i class='fa fa-commenting-o aria-hidden='true'></i></a>";
                    }
                    popupString += '<div class="row mp-popup">' +
                        '<div class="col-xs-6">' +
                        '<canvas id="statusPieChart_' + data[i].id + '" style="position: inherit !important; max-width:100px; max-height:100px"></canvas>' +
                        '</div>' +
                        '<div class="col-xs-6">' +
                        '<a href="#">' +
                        '<button onclick="MRManager.addTaskToMap(' + data[i].id + ', -1);" class="btn btn-block btn-success btn-sm">' + Messages('mapping.js.clustered.start') + '</button>' +
                        '</a>' +
                        '<a href="#">' +
                        '<button onclick="MRManager.viewChallenge(' + data[i].id + ');" class="btn btn-block btn-success btn-sm">' + Messages('mapping.js.clustered.view') + '</button>' +
                        '</a>';
                    if (LoggedInUser.id != -998) {
                        popupString += '<a href="#">' +
                            '<button onclick="MRManager.saveChallenge(' + LoggedInUser.userId + ',' + data[i].id + ');" class="btn btn-block btn-success btn-sm">' + Messages('mapping.js.clustered.save') + '</button>' +
                            '</a>'; 
                    }
                    popupString += '</div></div>';
                    marker.on("popupopen", popupFunction(data[i].id));
                } else {
                    var text = Messages('mapping.js.clustered.fix');
                    if (!TaskStatus.isAvailableToEdit(data[i].status)) {
                        text = Messages('mapping.js.clustered.view');
                    }
                    popupString += '<div><a href="#">' +
                        '<button onclick="MRManager.addTaskToMap(-1, ' + data[i].id + ');" class="btn btn-block btn-success btn-sm">' + text + '</button>' +
                        '</a></div>';
                }
                popupString += '</div>';
                marker.bindPopup(popupString, { maxHeight: 200, maxWidth: "auto" });

                markers.addLayer(marker);
            }
            map.fitBounds(markers.getBounds());
        } else {
            ToastUtils.Warning(Messages("mapping.js.search.notfound"));
        }
        currentTask.resetTask();
    };

    /**
     * Updates the map, this will first remove all current layers and then update the map with
     * the current task geometry
     */
    var updateTaskDisplay = function() {
        try {
            geojsonLayer.addData(currentTask.getData().geometry);
        } catch (err) {
            ToastUtils.Error("Invalid geometry supplied for task.");
            throw err;
        }
        // limit taskDisplay maxZoom by the default zoom set in the challenge.
        map.fitBounds(geojsonLayer.getBounds(), { maxZoom: map.options.zoom });
        controlPanel.update(signedIn, debugMode, true, true, true);
        resetEditControls();
        var challengeId = currentTask.getChallenge().getData().id;
        // update the browser url to reflect the current task
        window.history.pushState("", "", "/map/" + challengeId + "/" + currentTask.getData().id);
        // show the task text as a notification
        var taskInstruction = "##### " + Messages("mapping.js.instruction.challenge") + ": " + currentTask.getChallenge().getData().name + "\n---------\n\n";
        if (currentTask.getData().instruction === "") {
            taskInstruction += currentTask.getChallenge().getData().instruction;
        } else {
            taskInstruction += currentTask.getData().instruction;
        }
        var finalText = marked(taskInstruction + "\n\n------");
        finalText += Messages("mapping.js.instruction.status") + ": " + TaskStatus.getStatusName(currentTask.getData().status);
        if (typeof currentTask.getData().last_modified_user !== 'undefined') {
            finalText += "<br/>" + Messages('mapping.js.instruction.lastModifiedUser') + ": <a target='_blank' href='http://osm.org/user/" + currentTask.getData().last_modified_user + "'>" + currentTask.getData().last_modified_user + "</a>" +
                "<a target='_blank' href='https://www.openstreetmap.org/message/new/" + currentTask.getData().last_modified_user + "'> <i class='fa fa-commenting-o aria-hidden='true'></i></a>";
        }
        ToastUtils.Info(finalText, {timeOut: 0});
        // let the user know where they are
        displayAdminArea();
        updateChallengeInfo(currentTask.getData().parentId);
    };

    var resetEditControls = function() {
        editPanel.setAsEdit();
        editPanel.hide();
        updateMRControls();
    };

    // displays a spinning loading symbol when called
    var loading = function() {
        markers.clearLayers();
        geojsonLayer.clearLayers();
        toastr.clear();
        map.spin(true);
        // disable the map completely
        map.dragging.disable();
        map.touchZoom.disable();
        map.doubleClickZoom.disable();
        map.scrollWheelZoom.disable();
        map.boxZoom.disable();
        map.keyboard.disable();
        if (map.tap) map.tap.disable();
        $("#map").css("cursor", "progress");
    };

    // removes the spinning loading symbol
    var loaded = function() {
        map.spin(false);
        map.dragging.enable();
        map.touchZoom.enable();
        map.doubleClickZoom.enable();
        map.scrollWheelZoom.enable();
        map.boxZoom.enable();
        map.keyboard.enable();
        if (map.tap) map.tap.enable();
        $("#map").css("cursor", "grab").css("cursor", "-webkit-grab").css("cursor", "-moz-grab");
    };

    /**
     * Based on the challenge type (4 is Survey) will add or remove the survey panel from the map
     */
    var updateMRControls = function() {
        if (signedIn && !debugMode && currentTask.getChallenge().isSurvey()) {
            surveyPanel.updateSurvey(currentTask.getChallenge().getData().instruction,
                currentTask.getChallenge().getData().answers);
            surveyPanel.show();
        } else {
            surveyPanel.hide();
        }

        if (!signedIn || debugMode || currentTask.getChallenge().isSurvey()) {
            // disable the edit and false positive buttons in control if it is a survey
            controlPanel.disableControls();
        } else {
            controlPanel.enableControls();
        }
    };

    /**
     * Will display the current location that you are working in
     */
    var displayAdminArea = function () {
        Utils.getLocation(map.getCenter().lat, map.getCenter().lng, 
            function(data) {
                ToastUtils.Info(Utils.mqResultToString(data.address), {timeOut:10000});
            },
            function(data) {
                ToastUtils.Error(Messages('mapping.js.location.notfound'), {timeOut:10000});
            }
        );
    };

    /**
     * This function generally will only be called on a page load. So the initial entry into the
     * mapping area
     *
     * @param parentId This would be the id for a challenge, can be ignored if you are supplying the Task ID
     * @param taskId The taskId if you are looking for a specific task
     */
    var addTaskToMap = function(parentId, taskId) {
        currentTask.setSeedData(parentId, taskId);
        if (typeof taskId === 'undefined' || taskId == -1) {
            if (debugMode) {
                currentTask.getNextTask();
            }
            // if we are mapping directly using the challenge ID, then ignore whether it is enabled or not
            else if (typeof parentId !== 'undefined' && parentId != -1) {
                currentSearchParameters.setChallengeId(parentId);
                currentSearchParameters.setProjectEnabled(false);
                currentSearchParameters.setChallengeEnabled(false);
                currentTask.getRandomNextTask();
            } else {
                // In this case show all the challenges on the map
                loading();
                currentSearchParameters.setChallengeId(-1);
                currentSearchParameters.setProjectEnabled(true);
                currentSearchParameters.setChallengeEnabled(true);
                jsRoutes.org.maproulette.controllers.api.ProjectController.getClusteredPoints(-1).ajax({
                    success: MRManager.getSuccessHandler(MRManager.viewClusteredData),
                    error: MRManager.getErrorHandler()
                });
            }
        } else {
            currentTask.updateTask(taskId);
        }
    };

    /**
     * Shows the clustered data on the map, filtered by the parameters in the SearchParameters object
     * 
     * @param searchParameters
     */
    var getSearchedClusteredPoints = function(searchParameters, searchCallback) {
        loading();
        if (typeof searchParameters !== 'undefined') {
            currentSearchParameters = searchParameters;
        }  
        jsRoutes.org.maproulette.controllers.api.ProjectController.getSearchedClusteredPoints(currentSearchParameters.getCookieString()).ajax({
            success: function(data) {
                MRManager.getSuccessHandler(MRManager.viewClusteredData)(data);
                if (typeof searchCallback !== 'undefined') {
                    searchCallback(data);
                }
            },
            error: MRManager.getErrorHandler()
        });
    };

    /**
     * Gets the Next Task within the current search parameters. This function in debug mode will
     * retrieve the next task sequentially, in non-debug mode it will retrieve the next random task
     */
    var getNextTask = function(useDebugMode) {
        if (!signedIn || debugMode || useDebugMode) {
            currentTask.getNextTask();
        } else if (currentTask.getChallenge().isSurvey()) {
            currentTask.getRandomNextTask();
        } else {
            currentTask.setTaskStatus(TaskStatus.SKIPPED);
        }
    };

    /**
     * Gets the previous Task within the current search parameters, this function is only available
     * when in Debug Mode.
     */
    var getPreviousTask = function() {
        if (debugMode) {
            currentTask.getPreviousTask();
        }
    };

    /**
     * Sets the status for the current task
     *
     * @param status The status to set see Task.scala for information on the status ID's
     */
    var setTaskStatus = function(status) {
        currentTask.setTaskStatus(status);
    };

    /**
     * Answers the question for a survey.
     *
     * @param answerId
     */
    var answerSurveyQuestion = function(answerId) {
        currentTask.getChallenge().answerQuestion(currentTask.getData().id, answerId);
    };

    // registers a series of hotkeys for quick access to functions
    var registerHotKeys = function() {
        $(document).keydown(function(e) {
            if (disableKeys) {
                return;
            }
            e.preventDefault();
            switch(e.keyCode) {
                case 81: //q
                    // Get next task, set current task to false positive
                    if (!debugMode && !currentTask.getChallenge().isSurvey()) {
                        setTaskStatus(TaskStatus.FALSEPOSITIVE);
                    }
                    break;
                case 87: //w
                    // Get next task, skip current task
                    MRManager.getNextTask();
                    break;
                case 69: //e
                    // open task in ID
                    if (!debugMode && !currentTask.getChallenge().isSurvey()) {
                        openTaskInId();
                    }
                    break;
                case 82: //r
                    // open task in JOSM in current layer
                    if (!debugMode && !currentTask.getChallenge().isSurvey()) {
                        openTaskInJosm(false);
                    }
                    break;
                case 84: //t
                    // open task in JOSM in new layer
                    if (!debugMode && !currentTask.getChallenge().isSurvey()) {
                        openTaskInJosm(true);
                    }
                    break;
                case 27: //esc
                    // remove open dialog
                    resetEditControls();
                    break;
                default:
                    break;
            }
        });
    };

    var constructIdUri = function () {
        var zoom = map.getZoom();
        var center = map.getCenter();
        var lat = center.lat;
        var lon = center.lng;
        var baseUriComponent = "http://osm.org/edit?editor=id#";
        var idUriComponent = "id=";
        var mapUriComponent = "map=" + [zoom, lat, lon].join('/');
        // http://openstreetmap.us/iD/release/#background=Bing&id=w238383695,w238383626,&desmap=20.00/-77.02271/38.90085
        // https://www.openstreetmap.org/edit?editor=id&way=decode274204300#map=18/40.78479/-111.88787
        // https://www.openstreetmap.org/edit?editor=id#map=19/53.30938/-0.98069
        var currentGeometry = currentTask.getData().geometry;
        for (var i in currentGeometry.features) {
            var feature = currentGeometry.features[i];
            if (!feature.properties.osmid) {
                continue;
            }
            switch (feature.geometry.type) {
                case 'Point':
                    idUriComponent += "n" + feature.properties.osmid + ",";
                    break;
                case 'LineString':
                    idUriComponent += "w" + feature.properties.osmid + ",";
                    break;
                case 'Polygon':
                    idUriComponent += "w" + feature.properties.osmid + ",";
                    break;
                case 'MultiPolygon':
                    idUriComponent += "r" + feature.properties.osmid + ",";
                    break;
            }
        }
        // remove trailing comma - iD won't play ball with it
        idUriComponent = idUriComponent.replace(/,$/, "");
        return baseUriComponent + [idUriComponent, mapUriComponent, "comment=" + getCommentHashtags()].join('&');
    };

    var getCommentHashtags = function() {
        // add comment specific to challenge, make sure the name has no whitespace
        return "%23maproulette%20" + currentTask.getChallenge().getData().name.replace(/ /g, "_");
    };

    var openTaskInId = function () {
        // this opens a new tab and focuses the browser on it.
        // We may want to consider http://stackoverflow.com/a/11389138 to
        // open a tab in the background - seems like that trick does not
        // work in all browsers.
        window.open(constructIdUri(), 'MRIdWindow');
        ToastUtils.Info(Messages('mapping.js.task.loading'), {timeOut:10000});
        editPanel.setAsResult();
        setTimeout(editPanel.show(), 4000);
    };

    var constructJosmUri = function (new_layer) {
        var bounds = map.getBounds();
        var sw = bounds.getSouthWest();
        var ne = bounds.getNorthEast();
        var uri = 'http://127.0.0.1:8111/load_and_zoom?left=' + sw.lng + '&right=' + ne.lng +
            '&top=' + ne.lat + '&bottom=' + sw.lat + '&new_layer=' + (new_layer?'true':'false') +
            '&changeset_comment=' + getCommentHashtags() + '&select=';
        var selects = [];
        var currentGeometry = currentTask.getData().geometry;
        for (var f in currentGeometry.features) {
            var feature = currentGeometry.features[f];
            if (!feature.properties.osmid) {
                continue;
            }
            switch (feature.geometry.type) {
                case 'Point':
                    selects.push('node' + feature.properties.osmid);
                    break;
                case 'LineString':
                    selects.push('way' + feature.properties.osmid);
                    break;
                case 'Polygon':
                    selects.push('way' + feature.properties.osmid);
                    break;
                case 'MultiPolygon':
                    selects.push('relation' + feature.properties.osmid);
                    break;
            }
        }

        uri += selects.join(',');
        return uri;
    };

    var openTaskInJosm = function (new_layer) {
        new_layer = typeof new_layer !== 'undefined' ? new_layer : true;

        if (map.getZoom() < 14) {
            ToastUtils.Warning(Messages('mapping.js.task.zoomin'));
            return false;
        }
        var josmUri = constructJosmUri(new_layer);
        // Use the .ajax JQ method to load the JOSM link unobtrusively and alert when the JOSM plugin is not running.
        $.ajax({
            url: josmUri,
            success: function (t) {
                if (t.indexOf('OK') === -1) {
                    ToastUtils.Error(Messages('mapping.js.task.josm.noresponse'));
                } else {
                    editPanel.setAsResult();
                    setTimeout(editPanel.show(), 4000);
                }
            },
            error: function (e) {
                ToastUtils.Error(Messages('mapping.js.task.josm.noresponse'));
            }
        });
    };
    
    // This funtion returns the geoJSON of the currently displayed Task
    var getCurrentTaskGeoJSON = function() {
        if (currentTask.getData().id == -1) {
            return JSON.stringify(currentGeoJSON);
        }
        return JSON.stringify(currentTask.getData().geometry);
    };

    var isTaskLocked = function() {
        return currentTask.isLocked();
    };

    var isChallengeSurvey = function() {
        return currentTask.getChallenge().isSurvey();
    };

    var getSuccessHandler = function(success) {
        var handler = success;
        if (typeof success === 'undefined') {
            handler = MRManager.updateDisplayTask;
        }
        return function(data) {
            loaded();
            handler(data);
        };
    };

    var getErrorHandler = function(error) {
        var handler = error;
        if (typeof error === 'undefined') {
            handler = ToastUtils.handleError;
        }
        return function(err) {
            loaded();
            handler(err);
        };
    };

    /**
     * This will get the URL for the current item that is being displayed in the map
     * Search Parameters will be lost if you just refresh with this URL
     */
    var getCurrentMapURL = function() {
        return "/map/" + currentTask.getChallenge().getData().id + "/" + currentTask.getData().id;
    };
    
    var getCurrentTaskData = function() {
        return currentTask.getData();  
    };
    
    var getCurrentChallengeData = function() {
        return currentTask.getChallenge().getData();
    };

    var updateGeoJsonViewer = function() {
        disableKeys = true;
        $("#geojson_text").val(getCurrentTaskGeoJSON());
    };
    
    var viewChallenge = function(challengeId, filters) {
        if (typeof challengeId === 'undefined') {
            currentTask.getChallenge().view(currentTask.getChallenge().getData().id);
        } else {
            currentTask.getChallenge().view(challengeId, filters);
        }
    };

    var saveChallenge = function(userId, challengeId) {
        jsRoutes.org.maproulette.controllers.api.APIController.saveChallenge(userId, challengeId).ajax({
            success: function(data) {
                ToastUtils.Success(Messages("mapping.js.challenge.saved"));
            },
            error: MRManager.getErrorHandler()
        });
    };
    
    var getMapBounds = function() {
        return map.getBounds();  
    };

    var usingPriority = function() {
        return true;
    };

    return {
        init: init, 
        addTaskToMap: addTaskToMap,
        updateDisplayTask: updateTaskDisplay,
        getCurrentTaskGeoJSON: getCurrentTaskGeoJSON,
        getNextTask: getNextTask,
        getPreviousTask: getPreviousTask,
        setTaskStatus: setTaskStatus,
        isTaskLocked: isTaskLocked,
        getSuccessHandler: getSuccessHandler,
        getErrorHandler: getErrorHandler,
        answerSurveyQuestion: answerSurveyQuestion,
        updateMRControls: updateMRControls,
        isChallengeSurvey: isChallengeSurvey,
        openTaskInId: openTaskInId,
        openTaskInJosm: openTaskInJosm,
        getCurrentMapURL: getCurrentMapURL,
        getCurrentTaskData: getCurrentTaskData,
        getCurrentChallengeData: getCurrentChallengeData,
        updateGeoJsonViewer: updateGeoJsonViewer,
        viewGeoJsonData: viewGeoJsonData,
        viewClusteredData: viewClusteredData,
        viewChallenge: viewChallenge,
        saveChallenge: saveChallenge,
        usingPriority: usingPriority,
        loading:loading,
        loaded:loaded,
        getSearchedClusteredPoints:getSearchedClusteredPoints,
        getMapBounds:getMapBounds,
        updateMapOptions:updateMapOptions
    };

}());
