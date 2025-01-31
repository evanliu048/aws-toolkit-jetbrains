// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.popup

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.idea.AppMode
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_ENTER
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_ESCAPE
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.ComponentUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.AbstractPopup
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.util.messages.Topic
import com.intellij.util.ui.UIUtil
import software.amazon.awssdk.services.codewhispererruntime.model.Import
import software.amazon.awssdk.services.codewhispererruntime.model.Reference
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.codewhisperer.editor.CodeWhispererEditorManager
import software.aws.toolkits.jetbrains.services.codewhisperer.layout.CodeWhispererLayoutConfig.addHorizontalGlue
import software.aws.toolkits.jetbrains.services.codewhisperer.layout.CodeWhispererLayoutConfig.horizontalPanelConstraints
import software.aws.toolkits.jetbrains.services.codewhisperer.layout.CodeWhispererLayoutConfig.inlineLabelConstraints
import software.aws.toolkits.jetbrains.services.codewhisperer.model.DetailContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.model.PreviewContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.handlers.CodeWhispererEditorActionHandler
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.handlers.CodeWhispererPopupEnterHandler
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.handlers.CodeWhispererPopupEscHandler
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.handlers.CodeWhispererPopupTypedHandler
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.listeners.CodeWhispererAcceptButtonActionListener
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.listeners.CodeWhispererActionListener
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.listeners.CodeWhispererNextButtonActionListener
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.listeners.CodeWhispererPopupIntelliSenseAcceptListener
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.listeners.CodeWhispererPrevButtonActionListener
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.listeners.CodeWhispererScrollListener
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.listeners.addIntelliSenseAcceptListener
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererInvocationStatus
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererService
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.CodeWhispererTelemetryService
import software.aws.toolkits.jetbrains.services.codewhisperer.toolwindow.CodeWhispererCodeReferenceManager
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererColorUtil.POPUP_DIM_HEX
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.POPUP_INFO_TEXT_SIZE
import software.aws.toolkits.resources.message
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel

@Service
class CodeWhispererPopupManager {
    val popupComponents = CodeWhispererPopupComponents()

    var shouldListenerCancelPopup: Boolean = true
    var sessionContext = SessionContext()
        private set

    private var myPopup: JBPopup? = null

    init {
        // Listen for global scheme changes
        ApplicationManager.getApplication().messageBus.connect().subscribe(
            EditorColorsManager.TOPIC,
            EditorColorsListener { scheme ->
                if (scheme == null) return@EditorColorsListener
                popupComponents.apply {
                    panel.background = scheme.defaultBackground
                    panel.components.forEach {
                        it.background = scheme.getColor(EditorColors.DOCUMENTATION_COLOR)
                        it.foreground = scheme.defaultForeground
                    }
                    buttonsPanel.components.forEach {
                        it.foreground = UIUtil.getLabelForeground()
                    }
                    recommendationInfoLabel.foreground = UIUtil.getLabelForeground()
                    codeReferencePanel.components.forEach {
                        it.background = scheme.getColor(EditorColors.DOCUMENTATION_COLOR)
                        it.foreground = UIUtil.getLabelForeground()
                    }
                }
            }
        )
    }

    fun changeStates(
        states: InvocationContext,
        indexChange: Int,
        recommendationAdded: Boolean = false,
    ) {
        val (_, _, recommendationContext, popup) = states
        val (details) = recommendationContext
        if (recommendationAdded) {
            LOG.debug {
                "Add recommendations to the existing CodeWhisperer session, current number of recommendations: ${details.size}"
            }
            ApplicationManager.getApplication().messageBus.syncPublisher(CODEWHISPERER_POPUP_STATE_CHANGED)
                .recommendationAdded(states, sessionContext)
            return
        }
        val userInputOriginal = recommendationContext.userInputOriginal
        val userInput = recommendationContext.userInputSinceInvocation
        val typeaheadOriginal = run {
            val startOffset = states.requestContext.caretPosition.offset
            val currOffset = states.requestContext.editor.caretModel.offset
            if (startOffset > currOffset) {
                cancelPopup(popup)
                return
            }

            // userInput + typeahead
            val prefix = states.requestContext.editor.document.charsSequence
                .substring(startOffset, currOffset)
            if (prefix.length < userInputOriginal.length) {
                cancelPopup(popup)
                return
            } else {
                prefix.substring(userInputOriginal.length)
            }
        }
        val isReverse = indexChange < 0
        val validCount = getValidCount(details, userInput, typeaheadOriginal)
        val validSelectedIndex = getValidSelectedIndex(details, userInput, sessionContext.selectedIndex, typeaheadOriginal)
        if ((validSelectedIndex == validCount - 1 && indexChange == 1) ||
            (validSelectedIndex == 0 && indexChange == -1)
        ) {
            return
        }
        val selectedIndex = findNewSelectedIndex(
            isReverse,
            details,
            userInput,
            sessionContext.selectedIndex + indexChange,
            typeaheadOriginal
        )
        if (selectedIndex == -1 || !isValidRecommendation(details[selectedIndex], userInput, typeaheadOriginal)) {
            LOG.debug { "None of the recommendation is valid at this point, cancelling the popup" }
            cancelPopup(popup)
            return
        }
        val typeahead = resolveTypeahead(states, selectedIndex, typeaheadOriginal)
        sessionContext = SessionContext(
            typeahead,
            typeaheadOriginal,
            selectedIndex,
            sessionContext.seen,
            sessionContext.toBeRemovedHighlighter,
            isPopupShowing = sessionContext.isPopupShowing,
            perceivedLatency = sessionContext.perceivedLatency
        )

        ApplicationManager.getApplication().messageBus.syncPublisher(CODEWHISPERER_POPUP_STATE_CHANGED).stateChanged(
            states,
            sessionContext
        )
    }

    private fun resolveTypeahead(states: InvocationContext, selectedIndex: Int, typeahead: String): String {
        val recommendation = states.recommendationContext.details[selectedIndex].reformatted.content()
        val userInput = states.recommendationContext.userInputSinceInvocation
        var indexOfFirstNonWhiteSpace = typeahead.indexOfFirst { !it.isWhitespace() }
        if (indexOfFirstNonWhiteSpace == -1) {
            indexOfFirstNonWhiteSpace = typeahead.length
        }

        for (i in 0..indexOfFirstNonWhiteSpace) {
            val subTypeahead = typeahead.substring(i)
            if (recommendation.startsWith(userInput + subTypeahead)) return subTypeahead
        }
        return typeahead
    }

    fun updatePopupPanel(states: InvocationContext, sessionContext: SessionContext) {
        val userInput = states.recommendationContext.userInputSinceInvocation
        val details = states.recommendationContext.details
        val selectedIndex = sessionContext.selectedIndex
        val typeaheadOriginal = sessionContext.typeaheadOriginal
        val validCount = getValidCount(details, userInput, typeaheadOriginal)
        val validSelectedIndex = getValidSelectedIndex(details, userInput, selectedIndex, typeaheadOriginal)
        updateSelectedRecommendationLabelText(validSelectedIndex, validCount)
        updateNavigationPanel(validSelectedIndex, validCount)
        updateImportPanel(details[selectedIndex].recommendation.mostRelevantMissingImports())
        updateCodeReferencePanel(states.requestContext.project, details[selectedIndex].recommendation.references())
    }

    fun render(
        states: InvocationContext,
        sessionContext: SessionContext,
        isRecommendationAdded: Boolean,
    ) {
        updatePopupPanel(states, sessionContext)

        sessionContext.seen.add(sessionContext.selectedIndex)

        // There are four cases that render() is called:
        // 1. Popup showing for the first time, both booleans are false, we should show the popup and update the latency
        // end time, and emit the event if it's at the pagination end.
        // 2. New recommendations being added to the existing ones, we should not update the latency end time, and emit
        // the event if it's at the pagination end.
        // 3. User scrolling (so popup is changing positions), we should not update the latency end time and should not
        // emit any events.
        // 4. User navigating through the completions or typing as the completion shows. We should not update the latency
        // end time and should not emit any events in this case.
        if (!CodeWhispererInvocationStatus.getInstance().isDisplaySessionActive()) {
            states.requestContext.latencyContext.codewhispererPostprocessingEnd = System.nanoTime()
            states.requestContext.latencyContext.codewhispererEndToEndEnd = System.nanoTime()
            states.requestContext.latencyContext.perceivedLatency =
                states.requestContext.latencyContext.getPerceivedLatency(states.requestContext.triggerTypeInfo.triggerType)
        }
        if (!isRecommendationAdded) {
            showPopup(states, sessionContext, states.popup, visible = sessionContext.isPopupShowing)
        }
    }

    fun dontClosePopupAndRun(runnable: () -> Unit) {
        try {
            shouldListenerCancelPopup = false
            runnable()
        } finally {
            shouldListenerCancelPopup = true
        }
    }

    fun reset() {
        sessionContext = SessionContext()
    }

    fun cancelPopup(popup: JBPopup) {
        popup.cancel()
        Disposer.dispose(popup)
    }

    fun closePopup(popup: JBPopup) {
        popup.closeOk(null)
        Disposer.dispose(popup)
    }

    fun closePopup() {
        myPopup?.let {
            it.closeOk(null)
            Disposer.dispose(it)
        }
    }

    fun showPopup(
        states: InvocationContext,
        sessionContext: SessionContext,
        popup: JBPopup,
        visible: Boolean = false,
    ) {
        val caretPoint = states.requestContext.editor.offsetToXY(states.requestContext.caretPosition.offset)
        val editor = states.requestContext.editor
        val detailContexts = states.recommendationContext.details
        val userInputOriginal = states.recommendationContext.userInputOriginal
        val selectedIndex = sessionContext.selectedIndex
        val typeaheadOriginal = sessionContext.typeaheadOriginal
        val typeahead = sessionContext.typeahead
        val userInputLines = userInputOriginal.split("\n").size - 1
        val additionalLines = typeaheadOriginal.split("\n").size - typeahead.split("\n").size
        val popupSize = (popup as AbstractPopup).preferredContentSize
        val yAboveFirstLine = caretPoint.y - popupSize.height + (additionalLines + userInputLines) * editor.lineHeight
        val editorRect = editor.scrollingModel.visibleArea
        val popupRect = Rectangle(caretPoint.x, yAboveFirstLine, popupSize.width, popupSize.height)
        var noEnoughSpaceForPopup = false

        CodeWhispererInvocationStatus.getInstance().setDisplaySessionActive(true)

        if (!editorRect.contains(popupRect)) {
            // popup location above first line don't work, so don't show the popup
            noEnoughSpaceForPopup = true
        }

        // Check if the current editor still has focus. If not, don't show the popup.
        val isSameEditorAsTrigger = if (!AppMode.isRemoteDevHost()) {
            editor.contentComponent.isFocusOwner
        } else {
            FileEditorManager.getInstance(states.requestContext.project).selectedTextEditorWithRemotes.firstOrNull() == editor
        }
        if (!isSameEditorAsTrigger) {
            LOG.debug { "Current editor no longer has focus, not showing the popup" }
            cancelPopup(popup)
            return
        }

        // popup to always display above the current editing line
        val popupLocation = Point(caretPoint.x, yAboveFirstLine)

        val relativePopupLocationToEditor = RelativePoint(editor.contentComponent, popupLocation)

        // TODO: visibleAreaChanged listener is not getting triggered in remote environment when scrolling
        if (popup.isVisible) {
            // Changing the position of BackendBeAbstractPopup does not work
            if (!noEnoughSpaceForPopup && !AppMode.isRemoteDevHost()) {
                popup.setLocation(relativePopupLocationToEditor.screenPoint)
                popup.size = popup.preferredContentSize
            }
        } else {
            if (!AppMode.isRemoteDevHost()) {
                if (visible && !noEnoughSpaceForPopup) {
                    // TODO: will move to a keybinding listener once I found one
                    popupComponents.prevButton.text = popupComponents.prevButtonText()
                    popupComponents.nextButton.text = popupComponents.nextButtonText()
                    popup.show(relativePopupLocationToEditor)
                }
            } else {
                // TODO: For now, the popup will always display below the suggestions, without checking
                // if the location the popup is about to show at stays in the editor window or not, due to
                // the limitation of BackendBeAbstractPopup
                val caretVisualPosition = editor.offsetToVisualPosition(editor.caretModel.offset)

                // display popup x lines below the caret where x is # of lines of suggestions, since inlays don't
                // count as visual lines, the final math will always be just increment 1 line.
                val popupPositionForRemote = VisualPosition(
                    caretVisualPosition.line + 1,
                    caretVisualPosition.column
                )
                editor.putUserData(PopupFactoryImpl.ANCHOR_POPUP_POSITION, popupPositionForRemote)
                popup.showInBestPositionFor(editor)
            }
            if (sessionContext.perceivedLatency < 0) {
                val perceivedLatency = CodeWhispererInvocationStatus.getInstance().getTimeSinceDocumentChanged()
                CodeWhispererTelemetryService.getInstance().sendPerceivedLatencyEvent(
                    detailContexts[selectedIndex].requestId,
                    states.requestContext,
                    states.responseContext,
                    perceivedLatency
                )
                CodeWhispererTelemetryService.getInstance().sendClientComponentLatencyEvent(states)
                sessionContext.perceivedLatency = perceivedLatency
            }
        }

        bringSuggestionInlayToFront(editor, popup, sessionContext, !visible)
    }

    // opposite == false: show Q, hide IntelliSense
    // opposite == true: show IntelliSense, hide Q
    fun bringSuggestionInlayToFront(
        editor: Editor,
        popup: JBPopup?,
        sessionContext: SessionContext,
        opposite: Boolean = false,
    ) {
        val qInlinePopupAlpha = if (opposite) 1f else 0.1f
        val intelliSensePopupAlpha = if (opposite) 0f else 0.8f

        (popup as AbstractPopup?)?.popupWindow?.let {
            WindowManager.getInstance().setAlphaModeRatio(it, qInlinePopupAlpha)
        }
        ComponentUtil.getWindow(LookupManager.getActiveLookup(editor)?.component)?.let {
            WindowManager.getInstance().setAlphaModeRatio(it, intelliSensePopupAlpha)
        }
        sessionContext.isPopupShowing = !opposite
    }

    fun initPopup(): JBPopup = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(popupComponents.panel, null)
        .setAlpha(0.1F)
        .setCancelOnClickOutside(true)
        .setCancelOnOtherWindowOpen(true)
        .setCancelKeyEnabled(false)
        .setCancelOnWindowDeactivation(true)
        .createPopup().also {
            myPopup = it
        }

    fun getReformattedRecommendation(detailContext: DetailContext, userInput: String) =
        detailContext.reformatted.content().substring(userInput.length)

    fun initPopupListener(states: InvocationContext) {
        addPopupListener(states)
        states.requestContext.editor.scrollingModel.addVisibleAreaListener(CodeWhispererScrollListener(states), states)
        addButtonActionListeners(states)
        addMessageSubscribers(states)
        setPopupActionHandlers(states)
        addComponentListeners(states)
    }

    private fun addPopupListener(states: InvocationContext) {
        val listener = CodeWhispererPopupListener(states)
        states.popup.addListener(listener)
        Disposer.register(states) { states.popup.removeListener(listener) }
    }

    private fun addMessageSubscribers(states: InvocationContext) {
        val connect = ApplicationManager.getApplication().messageBus.connect(states)
        connect.subscribe(
            CODEWHISPERER_USER_ACTION_PERFORMED,
            object : CodeWhispererUserActionListener {
                override fun navigateNext(states: InvocationContext) {
                    changeStates(states, 1)
                }

                override fun navigatePrevious(states: InvocationContext) {
                    changeStates(states, -1)
                }

                override fun enter(states: InvocationContext) {
                    changeStates(states, 0)
                }

                override fun type(states: InvocationContext, diff: String) {
                    changeStates(states, 0)
                    val caretOffset = states.requestContext.editor.caretModel.primaryCaret.offset
                    val document = states.requestContext.editor.document
                    val text = document.charsSequence
                    if (caretOffset < text.length && diff == text[caretOffset].toString()) {
                        WriteCommandAction.runWriteCommandAction(states.requestContext.project) {
                            document.deleteString(caretOffset, caretOffset + 1)
                        }
                    }
                }

                override fun beforeAccept(states: InvocationContext, sessionContext: SessionContext) {
                    dontClosePopupAndRun {
                        CodeWhispererEditorManager.getInstance().updateEditorWithRecommendation(states, sessionContext)
                    }
                    closePopup(states.popup)
                    if (sessionContext.selectedIndex == 0) {
                        CodeWhispererService.getInstance().promoteNextInvocationIfAvailable()
                    }
                }
            }
        )
        states.requestContext.project.messageBus.connect(states).subscribe(
            LookupManagerListener.TOPIC,
            CodeWhispererPopupIntelliSenseAcceptListener(states)
        )
        LookupManager.getActiveLookup(states.requestContext.editor)?.let {
            addIntelliSenseAcceptListener(it, states)
        }
    }

    private fun addButtonActionListeners(states: InvocationContext) {
        popupComponents.prevButton.addButtonActionListener(CodeWhispererPrevButtonActionListener(states))
        popupComponents.nextButton.addButtonActionListener(CodeWhispererNextButtonActionListener(states))
        popupComponents.acceptButton.addButtonActionListener(CodeWhispererAcceptButtonActionListener(states))
    }

    private fun JButton.addButtonActionListener(listener: CodeWhispererActionListener) {
        this.addActionListener(listener)
        Disposer.register(listener.states) { this.removeActionListener(listener) }
    }

    private fun setPopupActionHandlers(states: InvocationContext) {
        states.requestContext.project.putUserData(KEY_INVOCATION_CONTEXT, states)
        setPopupTypedHandler(CodeWhispererPopupTypedHandler(TypedAction.getInstance().rawHandler, states))
        setPopupActionHandler(ACTION_EDITOR_ESCAPE, CodeWhispererPopupEscHandler(states))
        setPopupActionHandler(
            ACTION_EDITOR_ENTER,
            CodeWhispererPopupEnterHandler(EditorActionManager.getInstance().getActionHandler(ACTION_EDITOR_ENTER), states)
        )
    }

    private fun setPopupTypedHandler(newHandler: CodeWhispererPopupTypedHandler) {
        val oldTypedHandler = TypedAction.getInstance().setupRawHandler(newHandler)
        Disposer.register(newHandler.states) { TypedAction.getInstance().setupRawHandler(oldTypedHandler) }
    }

    private fun setPopupActionHandler(id: String, newHandler: CodeWhispererEditorActionHandler) {
        val oldHandler = EditorActionManager.getInstance().setActionHandler(id, newHandler)
        Disposer.register(newHandler.states) { EditorActionManager.getInstance().setActionHandler(id, oldHandler) }
    }

    private fun addComponentListeners(states: InvocationContext) {
        val editor = states.requestContext.editor
        val codewhispererSelectionListener: SelectionListener = object : SelectionListener {
            override fun selectionChanged(event: SelectionEvent) {
                if (shouldListenerCancelPopup) {
                    cancelPopup(states.popup)
                }
                super.selectionChanged(event)
            }
        }
        editor.selectionModel.addSelectionListener(codewhispererSelectionListener)
        Disposer.register(states) { editor.selectionModel.removeSelectionListener(codewhispererSelectionListener) }

        val codewhispererDocumentListener: DocumentListener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                super.documentChanged(event)
                val delete = event.newLength < event.oldLength
                if (!delete) return
                if (editor.caretModel.offset == event.offset) {
                    changeStates(states, 0)
                } else if (shouldListenerCancelPopup) {
                    cancelPopup(states.popup)
                }
            }
        }
        editor.document.addDocumentListener(codewhispererDocumentListener, states)

        val codewhispererCaretListener: CaretListener = object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                if (shouldListenerCancelPopup) {
                    cancelPopup(states.popup)
                }
                super.caretPositionChanged(event)
            }
        }
        editor.caretModel.addCaretListener(codewhispererCaretListener)
        Disposer.register(states) { editor.caretModel.removeCaretListener(codewhispererCaretListener) }

        val editorComponent = editor.contentComponent
        if (editorComponent.isShowing) {
            val window = ComponentUtil.getWindow(editorComponent)
            val windowListener: ComponentListener = object : ComponentAdapter() {
                override fun componentMoved(event: ComponentEvent) {
                    cancelPopup(states.popup)
                }

                override fun componentShown(e: ComponentEvent?) {
                    cancelPopup(states.popup)
                    super.componentShown(e)
                }
            }
            window?.addComponentListener(windowListener)
            Disposer.register(states) { window?.removeComponentListener(windowListener) }
        }

        val suggestionHoverEnterListener: EditorMouseMotionListener = object : EditorMouseMotionListener {
            override fun mouseMoved(e: EditorMouseEvent) {
                if (e.inlay != null) {
                    showPopup(states, sessionContext, states.popup, visible = true)
                } else {
                    bringSuggestionInlayToFront(editor, states.popup, sessionContext, opposite = true)
                }
                super.mouseMoved(e)
            }
        }
        editor.addEditorMouseMotionListener(suggestionHoverEnterListener, states)
    }

    private fun updateSelectedRecommendationLabelText(validSelectedIndex: Int, validCount: Int) {
        if (CodeWhispererInvocationStatus.getInstance().hasExistingServiceInvocation()) {
            popupComponents.recommendationInfoLabel.text = message("codewhisperer.popup.pagination_info")
            LOG.debug { "Pagination in progress. Current total: $validCount" }
        } else {
            popupComponents.recommendationInfoLabel.text =
                message(
                    "codewhisperer.popup.recommendation_info",
                    validSelectedIndex + 1,
                    validCount,
                    POPUP_DIM_HEX
                )
            LOG.debug { "Updated popup recommendation label text. Index: $validSelectedIndex, total: $validCount" }
        }
    }

    private fun updateNavigationPanel(validSelectedIndex: Int, validCount: Int) {
        val multipleRecommendation = validCount > 1
        popupComponents.prevButton.isEnabled = multipleRecommendation && validSelectedIndex != 0
        popupComponents.nextButton.isEnabled = multipleRecommendation && validSelectedIndex != validCount - 1
    }

    private fun updateImportPanel(imports: List<Import>) {
        popupComponents.panel.apply {
            if (components.contains(popupComponents.importPanel)) {
                remove(popupComponents.importPanel)
            }
        }
        if (imports.isEmpty()) return

        val firstImport = imports.first()
        val choice = if (imports.size > 2) 2 else imports.size - 1
        val message = message("codewhisperer.popup.import_info", firstImport.statement(), imports.size - 1, choice)
        popupComponents.panel.add(popupComponents.importPanel, horizontalPanelConstraints)
        popupComponents.importLabel.text = message
    }

    private fun updateCodeReferencePanel(project: Project, references: List<Reference>) {
        popupComponents.panel.apply {
            if (components.contains(popupComponents.codeReferencePanel)) {
                remove(popupComponents.codeReferencePanel)
            }
        }
        if (references.isEmpty()) return

        popupComponents.panel.add(popupComponents.codeReferencePanel, horizontalPanelConstraints)
        val licenses = references.map { it.licenseName() }.toSet()
        popupComponents.codeReferencePanelLink.apply {
            actionListeners.toList().forEach {
                removeActionListener(it)
            }
            addActionListener {
                CodeWhispererCodeReferenceManager.getInstance(project).showCodeReferencePanel()
            }
        }
        popupComponents.licenseCodePanel.apply {
            removeAll()
            add(popupComponents.licenseCodeLabelPrefixText, inlineLabelConstraints)
            licenses.forEachIndexed { i, license ->
                add(popupComponents.licenseLink(license), inlineLabelConstraints)
                if (i == licenses.size - 1) return@forEachIndexed
                add(JLabel(", "), inlineLabelConstraints)
            }

            add(JLabel(".  "), inlineLabelConstraints)
            add(popupComponents.codeReferencePanelLink, inlineLabelConstraints)
            addHorizontalGlue()
        }
        popupComponents.licenseCodePanel.components.forEach {
            if (it !is JComponent) return@forEach
            it.font = it.font.deriveFont(POPUP_INFO_TEXT_SIZE)
        }
    }

    private fun findNewSelectedIndex(
        isReverse: Boolean,
        detailContexts: List<DetailContext>,
        userInput: String,
        start: Int,
        typeahead: String,
    ): Int {
        val count = detailContexts.size
        val unit = if (isReverse) -1 else 1
        var currIndex: Int
        for (i in 0 until count) {
            currIndex = (start + i * unit) % count
            if (currIndex < 0) {
                currIndex += count
            }
            if (isValidRecommendation(detailContexts[currIndex], userInput, typeahead)) {
                return currIndex
            }
        }
        return -1
    }

    private fun getValidCount(detailContexts: List<DetailContext>, userInput: String, typeahead: String): Int =
        detailContexts.filter { isValidRecommendation(it, userInput, typeahead) }.size

    private fun getValidSelectedIndex(
        detailContexts: List<DetailContext>,
        userInput: String,
        selectedIndex: Int,
        typeahead: String,
    ): Int {
        var currIndexIgnoreInvalid = 0
        detailContexts.forEachIndexed { index, value ->
            if (index == selectedIndex) {
                return currIndexIgnoreInvalid
            }
            if (isValidRecommendation(value, userInput, typeahead)) {
                currIndexIgnoreInvalid++
            }
        }
        return -1
    }

    private fun isValidRecommendation(detailContext: DetailContext, userInput: String, typeahead: String): Boolean {
        if (detailContext.isDiscarded) return false
        if (detailContext.recommendation.content().isEmpty()) return false
        val indexOfFirstNonWhiteSpace = typeahead.indexOfFirst { !it.isWhitespace() }
        if (indexOfFirstNonWhiteSpace == -1) return true

        for (i in 0..indexOfFirstNonWhiteSpace) {
            val subTypeahead = typeahead.substring(i)
            if (detailContext.reformatted.content().startsWith(userInput + subTypeahead)) return true
        }
        return false
    }

    companion object {
        private val LOG = getLogger<CodeWhispererPopupManager>()
        fun getInstance(): CodeWhispererPopupManager = service()
        val KEY_INVOCATION_CONTEXT = Key.create<InvocationContext>("codewhisperer.states")
        val CODEWHISPERER_POPUP_STATE_CHANGED: Topic<CodeWhispererPopupStateChangeListener> = Topic.create(
            "CodeWhisperer popup state changed",
            CodeWhispererPopupStateChangeListener::class.java
        )
        val CODEWHISPERER_USER_ACTION_PERFORMED: Topic<CodeWhispererUserActionListener> = Topic.create(
            "CodeWhisperer user action performed",
            CodeWhispererUserActionListener::class.java
        )
    }
}

interface CodeWhispererPopupStateChangeListener {
    fun stateChanged(states: InvocationContext, sessionContext: SessionContext) {}
    fun scrolled(states: InvocationContext, sessionContext: SessionContext) {}
    fun recommendationAdded(states: InvocationContext, sessionContext: SessionContext) {}

    fun stateChanged(sessionContext: SessionContextNew) {}
    fun scrolled(sessionContext: SessionContextNew) {}
    fun recommendationAdded(states: InvocationContextNew, sessionContext: SessionContextNew) {}
}

interface CodeWhispererUserActionListener {
    fun enter(states: InvocationContext) {}
    fun type(states: InvocationContext, diff: String) {}
    fun navigatePrevious(states: InvocationContext) {}
    fun navigateNext(states: InvocationContext) {}
    fun beforeAccept(states: InvocationContext, sessionContext: SessionContext) {}
    fun afterAccept(states: InvocationContext, sessionContext: SessionContext, rangeMarker: RangeMarker) {}

    fun backspace(sessionContext: SessionContextNew, diff: String) {}
    fun enter(sessionContext: SessionContextNew, diff: String) {}
    fun type(sessionContext: SessionContextNew, diff: String) {}
    fun navigatePrevious(sessionContext: SessionContextNew) {}
    fun navigateNext(sessionContext: SessionContextNew) {}
    fun beforeAccept(sessionContext: SessionContextNew) {}
    fun afterAccept(states: InvocationContextNew, previews: List<PreviewContext>, sessionContext: SessionContextNew, rangeMarker: RangeMarker) {}
}
