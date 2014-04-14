var SidebarView = Backbone.View.extend({
    initialize: function () {
        //hide the help
        step.util.showOrHideTutorial(true);
            
        _.bindAll(this);

        //create tab container
        var container = this.$el.find(">div");
        this.tabContainer = this._createBaseTabs();
        this.$el.append(this._createTabHeadersContainer());
        this.$el.append(this.tabContainer);

        this.$el.on("show.bs.tab", this.changeMode);
        this.listenTo(this.model, "change", this.activate);
        this.listenTo(this.model, "toggleOpen", this.toggleOpen);

        this.activate();
    },
    changeMode: function (e) {
        var mode = null;
        var targetTab = $(e.target);
        var data = targetTab.data("target");
        if (data == '#lexicon') {
            mode = 'lexicon';
        } else if (data == '#analysis') {
            mode = 'analysis';
        } else if (data == '#history') {
            mode = 'history';
        } else if (data == '#help') {
            mode = 'help';
        }

        this.model.save({
            mode: mode
        });
    },
    createHelp: function () {
        var examplesContainer = $(".examplesContainer");
        examplesContainer.attr("id", "help").addClass("tab-pane");
        $(".tab-content").append(examplesContainer);
    },
    activate: function () {
        var self = this;
        //make sidebar visible
        this.$el.closest('.row-offcanvas').addClass('active');

        //show the right tab
        this.$el.find("[data-target='#" + this.model.get("mode") + "']").tab("show");

        if (this.model.get("mode") == 'lexicon') {
            this.lexicon.addClass("active");
            //load content
            var requestTime = new Date().getTime();
            $.getSafe(MODULE_GET_INFO, [this.model.get("strong"), this.model.get("morph")], function (data) {
                step.util.trackAnalytics("lexicon", "loaded", "time", new Date().getTime() - requestTime);
                step.util.trackAnalytics("passage", "strong", self.model.get("strong"));
                self.createDefinition(data);
            });
        } else if (this.model.get("mode") == 'analysis') {
            self.createAnalysis();
        } else if(this.model.get("mode") == 'history') {
            self.createHistory();
        } else {
            self.createHelp();
        }
    },
    _createBaseTabs: function () {
        var tabContent = $("<div class='tab-content'></div>");

        this.lexicon = $("<div id='lexicon' class='tab-pane'></div>");
        this.analysis = $("<div id='analysis' class='tab-pane'></div>");
        this.history = $("<div id='history' class='tab-pane'></div>");
        tabContent.append(this.lexicon);
        tabContent.append(this.analysis);
        tabContent.append(this.history);
        this.$el.append(tabContent);
        return tabContent;
    },
    createHistory: function () {
        if (!this.historyView) {
            this.historyView = new ViewHistory({
                el: this.history
            });
        } else {
            this.historyView.refresh();
        }
    },
    createAnalysis: function () {
        if (!this.analysisView) {
            this.analysisView = new ViewLexiconWordle({
                el: this.analysis
            });
        } else {
            this.analysisView.refresh();
        }
    },
    createDefinition: function (data) {
        //get definition tab
        this.lexicon.detach();
        this.lexicon.empty();

        var alternativeEntries = $("<div id='vocabEntries'>");
        this.lexicon.append(alternativeEntries);
        this.lexicon.append($("<h1>").append(__s.lexicon_vocab));

        if (data.vocabInfos.length == 0) {
            return;
        }

        if (data.vocabInfos.length > 1) {
            //multiple entries
            var panelGroup = $('<div class="panel-group" id="collapsedLexicon"></div>');
            for (var i = 0; i < data.vocabInfos.length; i++) {
                var item = data.vocabInfos[i];
                var hebrew = data.vocabInfos[i].strongNumber == 'H';
                var panelId = "lexicon-" + data.vocabInfos[i].strongNumber;
                var panelTitle = item.stepGloss + " (" + item.stepTransliteration + " - " + '<span class="' + (hebrew ? 'hbFontSmall' : 'unicodeFont') + '">' + item.accentedUnicode + "</span>)";
                var panelContentContainer = $('<div class="panel-collapse collapse">').attr("id", panelId);
                var panelBody = panelContentContainer.append($('<div class="panel-body"></div>'));
                panelContentContainer.append(panelBody);
                if (i == data.vocabInfos.length - 1) {
                    panelContentContainer.addClass("in");
                }

                this._createWordPanel(panelBody, item);
                var panelHeading = '<div class="panel-heading"><h4 class="panel-title" data-toggle="collapse" data-parent="#collapsedLexicon" data-target="#' + panelId + '"><a>' +
                    panelTitle + '</a></h4></div>';

                var panel = $('<div class="panel panel-default"></div>').append(panelHeading).append(panelContentContainer);
                panelGroup.append(panel);
            }
            this.lexicon.append(panelGroup);

        } else {
            this._createWordPanel(this.lexicon, data.vocabInfos[0]);
        }
        this.tabContainer.append(this.lexicon);
    },
    _createWordPanel: function (panel, mainWord) {
        panel.append(
            $("<div>").append($("<span>").addClass(mainWord.strongNumber[0] == 'H' ? "hbFontSmall" : "unicodeFont")
                .append(mainWord.accentedUnicode))
                .append(" (")
                .append(mainWord.stepTransliteration)
                .append("): ")
                .append(mainWord.shortDef || "")
                .append(" ")
                .append(mainWord.stepGloss)
        );

        panel
            .append($("<a></a>").attr("href", "javascript:void(0)").data("strongNumber", mainWord.strongNumber).append(__s.lexicon_search_for_this_word).click(function () {
                var args = "strong=" + encodeURIComponent($(this).data("strongNumber"));
                step.util.activePassage().save({ filter: ""});
                step.router.navigatePreserveVersions(args);
            })).append('<br />');
            

        // append the meanings
        if (mainWord.mediumDef) {
            panel.append($("<h2>").append(__s.lexicon_meaning));
            panel.append(mainWord.mediumDef);
        }

        //longer definitions
        if (mainWord.lsjDefs) {
            panel.append($("<h2>").append(mainWord.strongNumber[0].toLowerCase() == 'g' ? __s.lexicon_lsj_definition : __s.lexicon_bdb_definition));
            panel.append(mainWord.lsjDefs);
        }

        if (mainWord.relatedNos) {
            panel.append($("<h2>").append(__s.lexicon_related_words));
            var ul = $('<ul>');
            for (var i = 0; i < mainWord.relatedNos.length; i++) {
                var li = $("<li></li>").append($('<a href="javascript:void(0)">')
                    .append(mainWord.relatedNos[i].stepTransliteration)
                    .append(" (")
                    .append(mainWord.relatedNos[i].matchingForm)
                    .append(" - ")
                    .append(mainWord.relatedNos[i].gloss)
                    .append(")")
                    .data("strongNumber", mainWord.relatedNos[i].strongNumber));
                ul.append(li);
            }
            panel.append(ul);
            panel.find("a").click(function () {
                step.util.ui.showDef($(this).data("strongNumber"));
            });
        }
    },
    _createTabHeadersContainer: function () {
        var template = '<ul class="nav nav-tabs">' +
            '<li class="active"><a href="javascript:void(0)" class="glyphicon glyphicon-info-sign" title="<%= __s.original_word %>" data-toggle="tab" data-target="#lexicon"></li>' +
            '<li><a href="javascript:void(0)" class="glyphicon glyphicon-stats" title="<%= __s.passage_stats %>" data-toggle="tab" data-target="#analysis"></li>' +
            '<li><a href="javascript:void(0)" class="glyphicon glyphicon-bookmark" title="<%= __s.bookmarks_and_recent_texts %>" data-toggle="tab" data-target="#history"></li>' +
            '<li><a href="javascript:void(0)" class="stepglyph-help" title="<%= __s.quick_tutorial %>" data-toggle="tab" data-target="#help">?</li>' +
            '</ul>';

        var tabContainer = $(_.template(template)());

        //add close button
        tabContainer.append(
            $("<li class='closeSidebar'><a class='glyphicon glyphicon-remove' /></li>")
                .click(this.closeSidebar));

        return tabContainer;
    },
    toggleOpen: function() {
        if(!this.$el.closest('.row-offcanvas').hasClass("active")) {
            this.$el.closest('.row-offcanvas').addClass("active");
        } else {
            this.closeSidebar();
        }
    },
    closeSidebar: function () {
        this.$el.closest('.row-offcanvas').removeClass('active');
    }
});
