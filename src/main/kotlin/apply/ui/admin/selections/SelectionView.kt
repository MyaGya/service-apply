package apply.ui.admin.selections

import apply.application.ApplicantAndFormResponse
import apply.application.ApplicantService
import apply.application.EvaluationService
import apply.application.EvaluationTargetResponse
import apply.application.EvaluationTargetService
import apply.application.ExcelService
import apply.application.RecruitmentItemService
import apply.application.RecruitmentService
import apply.domain.applicationform.ApplicationForm
import apply.ui.admin.BaseLayout
import com.vaadin.flow.component.Component
import com.vaadin.flow.component.UI
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.H1
import com.vaadin.flow.component.html.H4
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.tabs.Tab
import com.vaadin.flow.component.tabs.Tabs
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.data.provider.DataProvider
import com.vaadin.flow.data.renderer.ComponentRenderer
import com.vaadin.flow.data.renderer.Renderer
import com.vaadin.flow.router.BeforeEvent
import com.vaadin.flow.router.HasUrlParameter
import com.vaadin.flow.router.Route
import com.vaadin.flow.router.WildcardParameter
import org.vaadin.klaudeta.PaginatedGrid
import support.views.addBackEndSortableColumn
import support.views.addBackEndSortableDateColumn
import support.views.addBackEndSortableDateTimeColumn
import support.views.addInMemorySortableColumn
import support.views.createNormalButton
import support.views.createPrimaryButton
import support.views.createPrimarySmallButton
import support.views.createSearchBar
import support.views.createSuccessButton
import support.views.downloadFile
import support.views.toMap

@Route(value = "admin/selections", layout = BaseLayout::class)
class SelectionView(
    private val applicantService: ApplicantService,
    private val recruitmentService: RecruitmentService,
    private val recruitmentItemService: RecruitmentItemService,
    private val evaluationService: EvaluationService,
    private val evaluationTargetService: EvaluationTargetService,
    private val excelService: ExcelService
) : VerticalLayout(), HasUrlParameter<Long> {
    private var recruitmentId = 0L
    private var evaluations = evaluationService.findAllByRecruitmentId(recruitmentId)
    private var tabs = Tabs()
    private var selectedTabIndex = 0

    private fun createTitle(): Component {
        return HorizontalLayout(H1(recruitmentService.getById(recruitmentId).title)).apply {
            setWidthFull()
            justifyContentMode = FlexComponent.JustifyContentMode.CENTER
        }
    }

    private fun createContent(keyword: String = ""): Component {
        val tabsToGrids: Map<Tab, Component> = mapTabAndGrid(keyword)
        val (tabs, grids) = createTabComponents(tabsToGrids)

        val menu = HorizontalLayout(
            createSearchBar {
                removeAll()
                add(
                    createTitle(),
                    createContent(keyword = it)
                )
                selectedTabIndex = tabs.selectedIndex
            },
            tabs,
            HorizontalLayout(
                createLoadButton(tabs),
                createDownloadButton()
            )
        ).apply {
            setWidthFull()
            justifyContentMode = FlexComponent.JustifyContentMode.BETWEEN
        }

        return VerticalLayout(menu, grids).apply { setWidthFull() }
    }

    private fun mapTabAndGrid(keyword: String): Map<Tab, Component> {
        val tabsToGrids = LinkedHashMap<Tab, Component>()

        tabsToGrids[Tab("전체 지원자")] = createTotalApplicantsGrid(keyword)

        evaluations = evaluationService.findAllByRecruitmentId(recruitmentId)
        for (evaluation in evaluations) {
            val evaluationTargetResponses =
                evaluationTargetService.findAllByEvaluationIdAndKeyword(evaluation.id, keyword)
            tabsToGrids[Tab(evaluation.title)] = createEvaluationTargetsGrid(evaluationTargetResponses)
        }
        return tabsToGrids
    }

    private fun createTotalApplicantsGrid(keyword: String): Component {
        return PaginatedGrid<ApplicantAndFormResponse>().apply {
            addBackEndSortableColumn("이름", "information.name", ApplicantAndFormResponse::name)
            addBackEndSortableColumn("이메일", "information.email", ApplicantAndFormResponse::email)
            addBackEndSortableColumn("전화번호", "information.phoneNumber", ApplicantAndFormResponse::phoneNumber)
            addBackEndSortableColumn("성별", "information.gender.title") { it.gender.title }
            addBackEndSortableDateColumn("생년월일", "information.birthday", ApplicantAndFormResponse::birthday)
            addBackEndSortableDateTimeColumn("지원 일시", "f.submittedDateTime") { it.applicationForm.submittedDateTime }
            addBackEndSortableColumn("부정 행위자", "c.id") { if (it.isCheater) "O" else "X" }
            addColumn(createButtonRenderer()).apply { isAutoWidth = true }
            pageSize = 10
            isMultiSort = true
            dataProvider = DataProvider.fromCallbacks(
                { query ->
                    applicantService.findAllByRecruitmentIdAndKeyword(
                        recruitmentId,
                        keyword,
                        query.offset,
                        query.limit,
                        query.sortOrders.toMap()
                    ).stream()
                },
                { evaluationService.count().toInt() }
            )
        }
    }

    private fun createButtonRenderer(): Renderer<ApplicantAndFormResponse> {
        return ComponentRenderer<Component, ApplicantAndFormResponse> { applicant ->
            createPrimarySmallButton("지원서") {
                val dialog = Dialog()
                dialog.add(*createRecruitmentItems(applicant.applicationForm))
                dialog.width = "800px"
                dialog.height = "90%"
                dialog.open()
            }
        }
    }

    private fun createEvaluationTargetsGrid(evaluationTargets: List<EvaluationTargetResponse>): Component {
        return Grid<EvaluationTargetResponse>(10).apply {
            addInMemorySortableColumn("이름", EvaluationTargetResponse::name)
            addInMemorySortableColumn("이메일", EvaluationTargetResponse::email)
            addInMemorySortableColumn("합계", EvaluationTargetResponse::totalScore)
            addInMemorySortableColumn("평가 상태", EvaluationTargetResponse::evaluationStatus)
            addInMemorySortableColumn("평가자", EvaluationTargetResponse::administratorId)
            addColumn(createEvaluationButtonRenderer()).apply { isAutoWidth = true }
            setItems(evaluationTargets)
        }
    }

    private fun createEvaluationButtonRenderer(): Renderer<EvaluationTargetResponse> {
        return ComponentRenderer<Component, EvaluationTargetResponse> { response ->
            createPrimarySmallButton("평가하기") {
                EvaluationTargetFormDialog(evaluationTargetService, response.id) {
                    selectedTabIndex = tabs.selectedIndex
                    removeAll()
                    add(
                        createTitle(),
                        createContent()
                    )
                }
            }
        }
    }

    private fun createTabComponents(tabsToGrids: Map<Tab, Component>): Pair<Tabs, Div> {
        val tabs = Tabs().apply {
            add(*(tabsToGrids.keys).toTypedArray())
            addSelectedChangeListener {
                tabsToGrids.forEach { (tab, grid) ->
                    grid.isVisible = (tab == selectedTab)
                }
            }
            setWidthFull()
            tabsToGrids.forEach { (tab, grid) -> grid.isVisible = (tab == selectedTab) }
            selectedIndex = selectedTabIndex
            tabs = this
        }

        val grids = Div(*tabsToGrids.values.toTypedArray()).apply { setWidthFull() }

        return tabs to grids
    }

    private fun createLoadButton(tabs: Tabs): Button {
        return createPrimaryButton("평가자 불러오기") {
            val evaluation = evaluations.first { it.title == tabs.selectedTab.label }
            evaluationTargetService.load(evaluation.id)
            selectedTabIndex = tabs.selectedIndex
            removeAll()
            add(
                createTitle(),
                createContent()
            )
        }
    }

    private fun createDownloadButton(): Button {
        return createSuccessButton("다운로드") {
            if (tabs.selectedIndex == 0) {
                val excel = excelService.createApplicantExcel(recruitmentId)
                downloadFile("${recruitmentService.getById(recruitmentId).title}.xlsx", excel)
            } else {
                val evaluation = evaluations[tabs.selectedIndex - 1]
                val excel = excelService.createTargetExcel(evaluation.id)
                downloadFile("${evaluation.title}.xlsx", excel)
            }
        }
    }

    private fun createRecruitmentItems(applicationForm: ApplicationForm): Array<Component> {
        val answers = applicationForm.answers
            .items
            .map { it.recruitmentItemId to it.contents }
            .toMap()
        val items = recruitmentItemService.findByRecruitmentIdOrderByPosition(recruitmentId)
            .map {
                createItem(it.title, createAnswer(answers.getOrDefault(it.id, "")))
            }.toTypedArray()
        return addIfExist(items, applicationForm.referenceUrl)
    }

    private fun addIfExist(items: Array<Component>, referenceUrl: String): Array<Component> {
        return when {
            referenceUrl.isNotEmpty() -> {
                val referenceItem = createItem(
                    "포트폴리오",
                    createNormalButton(referenceUrl) {
                        UI.getCurrent().page.open(referenceUrl)
                    }
                )
                items.plusElement(referenceItem)
            }
            else -> items
        }
    }

    private fun createItem(title: String, component: Component): Component {
        return Div(H4(title), component).apply {
            setWidthFull()
            justifyContentMode = FlexComponent.JustifyContentMode.START
        }
    }

    private fun createAnswer(answer: String): Component {
        return TextArea().apply {
            setWidthFull()
            isReadOnly = true
            value = answer
            justifyContentMode = FlexComponent.JustifyContentMode.START
        }
    }

    override fun setParameter(event: BeforeEvent, @WildcardParameter parameter: Long) {
        this.recruitmentId = parameter
        add(
            createTitle(),
            createContent()
        )
    }
}
