/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2005-2026 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.axelor.apps.bankpayment.service.bankreconciliation;

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.AccountManagement;
import com.axelor.apps.account.db.AccountingSituation;
import com.axelor.apps.account.db.FiscalPosition;
import com.axelor.apps.account.db.Journal;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.account.db.repo.AccountManagementRepository;
import com.axelor.apps.account.db.repo.AccountRepository;
import com.axelor.apps.account.db.repo.AccountingSituationRepository;
import com.axelor.apps.account.db.repo.JournalTypeRepository;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.account.service.TaxAccountService;
import com.axelor.apps.account.service.analytic.AnalyticLineService;
import com.axelor.apps.account.service.move.MoveCreateService;
import com.axelor.apps.account.service.move.MoveValidateService;
import com.axelor.apps.account.service.moveline.MoveLineComputeAnalyticService;
import com.axelor.apps.account.service.moveline.MoveLineCreateService;
import com.axelor.apps.account.service.moveline.MoveLineService;
import com.axelor.apps.account.service.moveline.MoveLineTaxService;
import com.axelor.apps.account.service.moveline.MoveLineToolService;
import com.axelor.apps.account.service.reconcile.ReconcileService;
import com.axelor.apps.bankpayment.db.BankReconciliation;
import com.axelor.apps.bankpayment.db.BankReconciliationLine;
import com.axelor.apps.bankpayment.db.BankStatementLine;
import com.axelor.apps.bankpayment.db.BankStatementLineAFB120;
import com.axelor.apps.bankpayment.db.BankStatementQuery;
import com.axelor.apps.bankpayment.db.BankStatementRule;
import com.axelor.apps.bankpayment.db.repo.BankReconciliationLineRepository;
import com.axelor.apps.bankpayment.db.repo.BankStatementRuleRepository;
import com.axelor.apps.bankpayment.exception.BankPaymentExceptionMessage;
import com.axelor.apps.bankpayment.service.bankstatementrule.BankStatementRuleService;
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.TraceBackRepository;
import com.axelor.apps.base.service.CurrencyScaleService;
import com.axelor.apps.base.service.exception.TraceBackService;
import com.axelor.apps.base.service.tax.TaxService;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.db.JPA;
import com.axelor.db.Model;
import com.axelor.db.Query;
import com.axelor.db.mapper.Mapper;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.rpc.Context;
import com.axelor.script.GroovyScriptHelper;
import com.axelor.text.GroovyTemplates;
import com.axelor.utils.helpers.StringHelper;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.inject.persist.Transactional;
import jakarta.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BankReconciliationMoveGenerationServiceImpl
    implements BankReconciliationMoveGenerationService {

  protected static final int AUTO_ACCOUNTING_BATCH_SIZE = 40;
  protected static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected BankReconciliationLineRepository bankReconciliationLineRepository;
  protected BankStatementRuleRepository bankStatementRuleRepository;
  protected BankReconciliationLineService bankReconciliationLineService;
  protected MoveValidateService moveValidateService;
  protected BankStatementRuleService bankStatementRuleService;
  protected ReconcileService reconcileService;
  protected MoveCreateService moveCreateService;
  protected MoveRepository moveRepository;
  protected AccountingSituationRepository accountingSituationRepository;
  protected TaxAccountService taxAccountService;
  protected MoveLineTaxService moveLineTaxService;
  protected TaxService taxService;
  protected MoveLineCreateService moveLineCreateService;
  protected MoveLineService moveLineService;
  protected CurrencyScaleService currencyScaleService;
  protected MoveLineToolService moveLineToolService;
  protected AccountManagementRepository accountManagementRepository;
  protected MoveLineComputeAnalyticService moveLineComputeAnalyticService;
  protected AnalyticLineService analyticLineService;

  @Inject
  public BankReconciliationMoveGenerationServiceImpl(
      BankReconciliationLineRepository bankReconciliationLineRepository,
      BankStatementRuleRepository bankStatementRuleRepository,
      BankReconciliationLineService bankReconciliationLineService,
      MoveValidateService moveValidateService,
      BankStatementRuleService bankStatementRuleService,
      ReconcileService reconcileService,
      MoveCreateService moveCreateService,
      MoveRepository moveRepository,
      AccountingSituationRepository accountingSituationRepository,
      TaxAccountService taxAccountService,
      MoveLineTaxService moveLineTaxService,
      TaxService taxService,
      MoveLineCreateService moveLineCreateService,
      MoveLineService moveLineService,
      CurrencyScaleService currencyScaleService,
      MoveLineToolService moveLineToolService,
      AccountManagementRepository accountManagementRepository,
      MoveLineComputeAnalyticService moveLineComputeAnalyticService,
      AnalyticLineService analyticLineService) {
    this.bankReconciliationLineRepository = bankReconciliationLineRepository;
    this.bankStatementRuleRepository = bankStatementRuleRepository;
    this.bankReconciliationLineService = bankReconciliationLineService;
    this.moveValidateService = moveValidateService;
    this.bankStatementRuleService = bankStatementRuleService;
    this.reconcileService = reconcileService;
    this.moveCreateService = moveCreateService;
    this.moveRepository = moveRepository;
    this.accountingSituationRepository = accountingSituationRepository;
    this.taxAccountService = taxAccountService;
    this.moveLineTaxService = moveLineTaxService;
    this.taxService = taxService;
    this.moveLineCreateService = moveLineCreateService;
    this.moveLineService = moveLineService;
    this.currencyScaleService = currencyScaleService;
    this.moveLineToolService = moveLineToolService;
    this.accountManagementRepository = accountManagementRepository;
    this.moveLineComputeAnalyticService = moveLineComputeAnalyticService;
    this.analyticLineService = analyticLineService;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void generateMovesAutoAccounting(BankReconciliation bankReconciliation)
      throws AxelorException {
    long startTime = System.nanoTime();
    int pageNumber = 0;
    int totalFetchedLineCount = 0;
    int totalEligibleLineCount = 0;
    int totalResolvedLineCount = 0;
    int offset = 0;

    LOG.info(
        "Starting auto-accounting move generation for bank reconciliation {} with batch size {}",
        bankReconciliation.getId(),
        AUTO_ACCOUNTING_BATCH_SIZE);

    List<BankReconciliationLine> bankReconciliationLines =
        bankReconciliationLineRepository
            .findByBankReconciliation(bankReconciliation)
            .fetch(AUTO_ACCOUNTING_BATCH_SIZE, offset);

    while (bankReconciliationLines.size() > 0) {
      pageNumber++;
      totalFetchedLineCount += bankReconciliationLines.size();
      Map<AutoAccountingRuleKey, List<PreparedAutoAccountingRule>>
          preparedAutoAccountingRulesByKey =
              groupPreparedAutoAccountingRules(
                  prepareAutoAccountingRules(
                      bankReconciliation, collectAutoAccountingRuleKeys(bankReconciliationLines)));
      List<AutoAccountingWorkItem> autoAccountingWorkItems =
          collectAutoAccountingWorkItems(bankReconciliationLines, preparedAutoAccountingRulesByKey);

      int preparedRuleCount =
          preparedAutoAccountingRulesByKey.values().stream().mapToInt(List::size).sum();
      int pageEligibleLineCount = autoAccountingWorkItems.size();
      totalEligibleLineCount += pageEligibleLineCount;

      LOG.info(
          "Auto-accounting page {} for bank reconciliation {}: fetched {} lines, {} eligible lines, {} prepared rules",
          pageNumber,
          bankReconciliation.getId(),
          bankReconciliationLines.size(),
          pageEligibleLineCount,
          preparedRuleCount);

      for (AutoAccountingWorkItem autoAccountingWorkItem : autoAccountingWorkItems) {
        processAutoAccountingWorkItem(autoAccountingWorkItem, bankReconciliation);
      }

      int pageResolvedLineCount =
          (int)
              autoAccountingWorkItems.stream()
                  .filter(
                      autoAccountingWorkItem ->
                          autoAccountingWorkItem.getBankReconciliationLine().getMoveLine() != null)
                  .count();
      totalResolvedLineCount += pageResolvedLineCount;

      LOG.info(
          "Completed auto-accounting page {} for bank reconciliation {}: resolved {} of {} eligible lines",
          pageNumber,
          bankReconciliation.getId(),
          pageResolvedLineCount,
          pageEligibleLineCount);

      offset += AUTO_ACCOUNTING_BATCH_SIZE;
      JPA.clear();
      bankReconciliationLines =
          bankReconciliationLineRepository
              .findByBankReconciliation(bankReconciliation)
              .fetch(AUTO_ACCOUNTING_BATCH_SIZE, offset);
    }

    LOG.info(
        "Completed auto-accounting move generation for bank reconciliation {} in {} ms: {} pages, {} fetched lines, {} eligible lines, {} resolved lines",
        bankReconciliation.getId(),
        (System.nanoTime() - startTime) / 1_000_000,
        pageNumber,
        totalFetchedLineCount,
        totalEligibleLineCount,
        totalResolvedLineCount);
  }

  protected List<BankStatementRule> fetchAutoAccountingRules(
      BankReconciliation bankReconciliation, Set<AutoAccountingRuleKey> autoAccountingRuleKeys) {
    if (ObjectUtils.isEmpty(autoAccountingRuleKeys)) {
      return Collections.emptyList();
    }

    List<AutoAccountingRuleKey> sortedAutoAccountingRuleKeys =
        new ArrayList<>(autoAccountingRuleKeys);
    sortedAutoAccountingRuleKeys.sort(
        Comparator.comparing((AutoAccountingRuleKey key) -> key.bankDetailsId)
            .thenComparing(key -> key.interbankCodeLineId));

    Query<BankStatementRule> query =
        bankStatementRuleRepository
            .all()
            .filter(
                "self.ruleTypeSelect = :ruleTypeSelect"
                    + " AND self.accountManagement.company = :company"
                    + " AND ("
                    + buildAutoAccountingRulePairFilter(sortedAutoAccountingRuleKeys)
                    + ")")
            .bind("ruleTypeSelect", BankStatementRuleRepository.RULE_TYPE_ACCOUNTING_AUTO)
            .bind("company", bankReconciliation.getCompany());

    return bindAutoAccountingRulePairs(query, sortedAutoAccountingRuleKeys).fetch();
  }

  protected String buildAutoAccountingRulePairFilter(
      List<AutoAccountingRuleKey> autoAccountingRuleKeys) {
    StringBuilder filter = new StringBuilder();
    for (int i = 0; i < autoAccountingRuleKeys.size(); i++) {
      if (i > 0) {
        filter.append(" OR ");
      }
      filter
          .append("(self.accountManagement.bankDetails.id = :bankDetailsId")
          .append(i)
          .append(" AND self.accountManagement.interbankCodeLine.id = :interbankCodeLineId")
          .append(i)
          .append(")");
    }
    return filter.toString();
  }

  protected Query<BankStatementRule> bindAutoAccountingRulePairs(
      Query<BankStatementRule> query, List<AutoAccountingRuleKey> autoAccountingRuleKeys) {
    for (int i = 0; i < autoAccountingRuleKeys.size(); i++) {
      AutoAccountingRuleKey autoAccountingRuleKey = autoAccountingRuleKeys.get(i);
      query.bind("bankDetailsId" + i, autoAccountingRuleKey.bankDetailsId);
      query.bind("interbankCodeLineId" + i, autoAccountingRuleKey.interbankCodeLineId);
    }
    return query;
  }

  protected List<PreparedAutoAccountingRule> prepareAutoAccountingRules(
      BankReconciliation bankReconciliation, Set<AutoAccountingRuleKey> autoAccountingRuleKeys) {
    List<PreparedAutoAccountingRule> preparedAutoAccountingRules = new ArrayList<>();
    for (BankStatementRule autoAccountingRule :
        fetchAutoAccountingRules(bankReconciliation, autoAccountingRuleKeys)) {
      preparedAutoAccountingRules.add(new PreparedAutoAccountingRule(autoAccountingRule));
    }
    return preparedAutoAccountingRules;
  }

  protected Set<AutoAccountingRuleKey> collectAutoAccountingRuleKeys(
      List<BankReconciliationLine> bankReconciliationLines) {
    Set<AutoAccountingRuleKey> autoAccountingRuleKeys = new HashSet<>();
    for (BankReconciliationLine bankReconciliationLine : bankReconciliationLines) {
      if (bankReconciliationLine.getMoveLine() != null
          || bankReconciliationLine.getBankStatementLine() == null) {
        continue;
      }
      AutoAccountingRuleKey key =
          AutoAccountingRuleKey.of(bankReconciliationLine.getBankStatementLine());
      if (key != null) {
        autoAccountingRuleKeys.add(key);
      }
    }
    return autoAccountingRuleKeys;
  }

  protected List<PreparedAutoAccountingRule> getPreparedAutoAccountingRules(
      Map<AutoAccountingRuleKey, List<PreparedAutoAccountingRule>> preparedAutoAccountingRulesByKey,
      BankStatementLine bankStatementLine) {
    AutoAccountingRuleKey key = AutoAccountingRuleKey.of(bankStatementLine);
    if (key != null) {
      return preparedAutoAccountingRulesByKey.getOrDefault(key, Collections.emptyList());
    }
    return Collections.emptyList();
  }

  protected Map<AutoAccountingRuleKey, List<PreparedAutoAccountingRule>>
      groupPreparedAutoAccountingRules(
          List<PreparedAutoAccountingRule> preparedAutoAccountingRules) {
    Map<AutoAccountingRuleKey, List<PreparedAutoAccountingRule>> preparedAutoAccountingRulesByKey =
        new HashMap<>();
    for (PreparedAutoAccountingRule preparedAutoAccountingRule : preparedAutoAccountingRules) {
      AutoAccountingRuleKey key =
          AutoAccountingRuleKey.of(preparedAutoAccountingRule.getBankStatementRule());
      if (key == null) {
        continue;
      }
      preparedAutoAccountingRulesByKey
          .computeIfAbsent(key, ignored -> new ArrayList<>())
          .add(preparedAutoAccountingRule);
    }
    return preparedAutoAccountingRulesByKey;
  }

  protected List<AutoAccountingWorkItem> collectAutoAccountingWorkItems(
      List<BankReconciliationLine> bankReconciliationLines,
      Map<AutoAccountingRuleKey, List<PreparedAutoAccountingRule>>
          preparedAutoAccountingRulesByKey) {
    List<AutoAccountingWorkItem> autoAccountingWorkItems = new ArrayList<>();
    for (BankReconciliationLine bankReconciliationLine : bankReconciliationLines) {
      if (bankReconciliationLine.getMoveLine() != null
          || bankReconciliationLine.getBankStatementLine() == null) {
        continue;
      }
      BankStatementLine bankStatementLine = bankReconciliationLine.getBankStatementLine();
      Context scriptContext =
          new Context(Mapper.toMap(bankStatementLine), BankStatementLineAFB120.class);
      autoAccountingWorkItems.add(
          new AutoAccountingWorkItem(
              bankReconciliationLine,
              bankStatementLine,
              new GroovyScriptHelper(scriptContext),
              getPreparedAutoAccountingRules(preparedAutoAccountingRulesByKey, bankStatementLine)));
    }
    return autoAccountingWorkItems;
  }

  protected void processAutoAccountingWorkItem(
      AutoAccountingWorkItem autoAccountingWorkItem, BankReconciliation bankReconciliation)
      throws AxelorException {
    BankReconciliationLine bankReconciliationLine =
        autoAccountingWorkItem.getBankReconciliationLine();
    Move move;

    for (PreparedAutoAccountingRule preparedAutoAccountingRule :
        autoAccountingWorkItem.getPreparedAutoAccountingRules()) {
      BankStatementRule bankStatementRule = preparedAutoAccountingRule.getBankStatementRule();

      if (bankStatementRule.getBankStatementQuery() != null
          && !Strings.isNullOrEmpty(preparedAutoAccountingRule.getPreparedQuery())
          && Boolean.TRUE.equals(
              autoAccountingWorkItem
                  .getGroovyScriptHelper()
                  .eval(preparedAutoAccountingRule.getPreparedQuery()))) {

        checkAccountBeforeAutoAccounting(bankStatementRule, bankReconciliation);

        if (bankStatementRule.getAccountManagement().getJournal() == null) {
          continue;
        }

        MoveLine moveLine =
            Optional.of(autoAccountingWorkItem)
                .map(AutoAccountingWorkItem::getBankStatementLine)
                .map(BankStatementLine::getMoveLine)
                .orElse(null);
        if (moveLine != null) {
          bankReconciliationLineService.reconcileBRLAndMoveLine(bankReconciliationLine, moveLine);
          move = moveLine.getMove();
        } else {
          move = generateMove(bankReconciliationLine, bankStatementRule);
          moveValidateService.accounting(move);
        }
        if (bankStatementRule.getLetterToInvoice()) {
          letterToInvoice(bankStatementRule, bankReconciliationLine, move);
        }
        break;
      }
    }

    if (bankReconciliationLine.getMoveLine() == null
        && bankReconciliationLine.getAccount() != null
        && bankReconciliation.getCashAccount() != null
        && bankReconciliation.getJournal() != null) {
      move = generateMove(bankReconciliationLine, null);
      moveValidateService.accounting(move);
    }
    if (bankReconciliationLine.getMoveLine() == null) {
      manageDynamicSearchOnMoveLines(bankReconciliationLine);
    }
  }

  protected void letterToInvoice(
      BankStatementRule bankStatementRule, BankReconciliationLine bankReconciliationLine, Move move)
      throws AxelorException {

    MoveLine fetchedMoveLine =
        bankStatementRuleService
            .getMoveLine(bankStatementRule, bankReconciliationLine, move)
            .orElse(null);
    // Will reconcile move line that has as account the counterpart account specified in
    // bankstatementrule
    MoveLine generatedMoveLineToLetter =
        move.getMoveLineList().stream()
            .filter(
                moveLine -> moveLine.getAccount().equals(bankStatementRule.getCounterpartAccount()))
            .findFirst()
            .orElse(null);
    if (fetchedMoveLine != null && generatedMoveLineToLetter != null) {
      if (generatedMoveLineToLetter.getDebit().signum() > 0) {
        reconcileService.reconcile(generatedMoveLineToLetter, fetchedMoveLine, false, true);
      } else {
        reconcileService.reconcile(fetchedMoveLine, generatedMoveLineToLetter, false, true);
      }
    }
  }

  protected static final class AutoAccountingRuleKey {

    private final Long bankDetailsId;
    private final Long interbankCodeLineId;

    protected AutoAccountingRuleKey(Long bankDetailsId, Long interbankCodeLineId) {
      this.bankDetailsId = bankDetailsId;
      this.interbankCodeLineId = interbankCodeLineId;
    }

    protected static AutoAccountingRuleKey of(BankStatementRule bankStatementRule) {
      if (bankStatementRule == null || bankStatementRule.getAccountManagement() == null) {
        return null;
      }
      Long bankDetailsId =
          Optional.ofNullable(bankStatementRule.getAccountManagement().getBankDetails())
              .map(Model::getId)
              .orElse(null);
      Long interbankCodeLineId =
          Optional.ofNullable(bankStatementRule.getAccountManagement().getInterbankCodeLine())
              .map(Model::getId)
              .orElse(null);
      return create(bankDetailsId, interbankCodeLineId);
    }

    protected static AutoAccountingRuleKey of(BankStatementLine bankStatementLine) {
      if (bankStatementLine == null) {
        return null;
      }
      Long bankDetailsId =
          Optional.ofNullable(bankStatementLine.getBankDetails()).map(Model::getId).orElse(null);
      Long interbankCodeLineId =
          Optional.ofNullable(bankStatementLine.getOperationInterbankCodeLine())
              .map(Model::getId)
              .orElse(null);
      return create(bankDetailsId, interbankCodeLineId);
    }

    protected static AutoAccountingRuleKey create(Long bankDetailsId, Long interbankCodeLineId) {
      if (bankDetailsId == null || interbankCodeLineId == null) {
        return null;
      }
      return new AutoAccountingRuleKey(bankDetailsId, interbankCodeLineId);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof AutoAccountingRuleKey)) {
        return false;
      }
      AutoAccountingRuleKey that = (AutoAccountingRuleKey) o;
      return Objects.equals(bankDetailsId, that.bankDetailsId)
          && Objects.equals(interbankCodeLineId, that.interbankCodeLineId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(bankDetailsId, interbankCodeLineId);
    }
  }

  protected static final class PreparedAutoAccountingRule {

    private final BankStatementRule bankStatementRule;
    private final String preparedQuery;

    protected PreparedAutoAccountingRule(BankStatementRule bankStatementRule) {
      this.bankStatementRule = bankStatementRule;
      this.preparedQuery =
          Optional.ofNullable(bankStatementRule)
              .map(BankStatementRule::getBankStatementQuery)
              .map(BankStatementQuery::getQuery)
              .filter(StringUtils::notBlank)
              .map(
                  query -> query.replaceAll("%s", "\"" + bankStatementRule.getSearchLabel() + "\""))
              .orElse(null);
    }

    protected BankStatementRule getBankStatementRule() {
      return bankStatementRule;
    }

    protected String getPreparedQuery() {
      return preparedQuery;
    }
  }

  protected static final class AutoAccountingWorkItem {

    private final BankReconciliationLine bankReconciliationLine;
    private final BankStatementLine bankStatementLine;
    private final GroovyScriptHelper groovyScriptHelper;
    private final List<PreparedAutoAccountingRule> preparedAutoAccountingRules;

    protected AutoAccountingWorkItem(
        BankReconciliationLine bankReconciliationLine,
        BankStatementLine bankStatementLine,
        GroovyScriptHelper groovyScriptHelper,
        List<PreparedAutoAccountingRule> preparedAutoAccountingRules) {
      this.bankReconciliationLine = bankReconciliationLine;
      this.bankStatementLine = bankStatementLine;
      this.groovyScriptHelper = groovyScriptHelper;
      this.preparedAutoAccountingRules = preparedAutoAccountingRules;
    }

    protected BankReconciliationLine getBankReconciliationLine() {
      return bankReconciliationLine;
    }

    protected BankStatementLine getBankStatementLine() {
      return bankStatementLine;
    }

    protected GroovyScriptHelper getGroovyScriptHelper() {
      return groovyScriptHelper;
    }

    protected List<PreparedAutoAccountingRule> getPreparedAutoAccountingRules() {
      return preparedAutoAccountingRules;
    }
  }

  @Override
  public Move generateMove(
      BankReconciliationLine bankReconciliationLine, BankStatementRule bankStatementRule)
      throws AxelorException {
    BankStatementLine bankStatementLine = bankReconciliationLine.getBankStatementLine();

    BankReconciliation bankReconciliation = bankReconciliationLine.getBankReconciliation();

    Move move =
        generateMove(
            bankReconciliation, bankReconciliationLine, bankStatementLine, bankStatementRule);

    MoveLine counterPartMoveLine =
        generateMoveLine(bankReconciliationLine, bankStatementRule, move, true, null);

    MoveLine moveLine =
        generateMoveLine(bankReconciliationLine, bankStatementRule, move, false, null);

    generateTaxMoveLine(counterPartMoveLine, moveLine);

    bankReconciliationLineService.reconcileBRLAndMoveLine(bankReconciliationLine, moveLine);

    return moveRepository.save(move);
  }

  protected String computeOrigin(
      BankReconciliationLine bankReconciliationLine,
      BankStatementLine bankStatementLine,
      String rule) {
    try {
      Object origin = computeLabel(bankReconciliationLine, rule);
      if (ObjectUtils.notEmpty(origin)) {
        return origin.toString();
      }
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
    return bankStatementLine != null ? bankStatementLine.getOrigin() : null;
  }

  protected String computeDescription(
      BankReconciliationLine bankReconciliationLine,
      BankStatementLine bankStatementLine,
      String rule) {
    String description = "";

    try {
      Object desc = computeLabel(bankReconciliationLine, rule);
      if (ObjectUtils.notEmpty(desc)) {
        return desc.toString();
      }
    } catch (Exception e) {
      TraceBackService.trace(e);
    }

    if (bankStatementLine != null) {
      description = description.concat(bankStatementLine.getDescription());
    }
    description = StringHelper.cutTooLongString(description);

    if (!Strings.isNullOrEmpty(bankReconciliationLine.getReference())) {
      String reference = "ref:";
      reference =
          StringHelper.cutTooLongString(reference.concat(bankReconciliationLine.getReference()));
      description = StringHelper.cutTooLongStringWithOffset(description, reference.length());
      description = description.concat(reference);
    }
    return description;
  }

  protected Object computeLabel(Model model, String rule) {
    if (StringUtils.isEmpty(rule)) {
      return null;
    }
    Context scriptContext = new Context(Mapper.toMap(model), model.getClass());
    return Beans.get(GroovyTemplates.class).fromText(rule).make(scriptContext).render();
  }

  protected void generateTaxMoveLine(MoveLine counterPartMoveLine, MoveLine moveLine)
      throws AxelorException {
    int vatSystemSelect = AccountRepository.VAT_SYSTEM_DEFAULT;
    Move move = counterPartMoveLine.getMove();
    Journal journal = move.getJournal();
    int journalTechnicalType = journal.getJournalType().getTechnicalTypeSelect();
    Company company = counterPartMoveLine.getMove().getCompany();
    Partner partner = null;

    if (journalTechnicalType == JournalTypeRepository.TECHNICAL_TYPE_SELECT_EXPENSE) {
      partner = counterPartMoveLine.getPartner();
    } else if (journalTechnicalType == JournalTypeRepository.TECHNICAL_TYPE_SELECT_SALE) {
      partner = company.getPartner();
    }

    if (partner != null) {
      AccountingSituation accountingSituation =
          accountingSituationRepository.findByCompanyAndPartner(company, partner);
      if (accountingSituation != null && accountingSituation.getVatSystemSelect() != null) {
        vatSystemSelect = accountingSituation.getVatSystemSelect();
      }
    }

    Account counterPartAccount = counterPartMoveLine.getAccount();
    if (vatSystemSelect == AccountRepository.VAT_SYSTEM_DEFAULT && counterPartAccount != null) {
      vatSystemSelect = counterPartAccount.getVatSystemSelect();
    }

    Set<TaxLine> taxLineSet = counterPartMoveLine.getTaxLineSet();
    if (ObjectUtils.notEmpty(taxLineSet)) {
      for (TaxLine taxLine : taxLineSet) {
        Account account =
            taxAccountService.getAccount(
                taxLine != null ? taxLine.getTax() : null,
                company,
                journal,
                moveLine.getAccount(),
                vatSystemSelect,
                false,
                move.getFunctionalOriginSelect());
        moveLineTaxService.autoTaxLineGenerate(move, account, false);
      }
    }

    fixTaxAmountRounding(move, counterPartMoveLine, moveLine);
  }

  protected void fixTaxAmountRounding(Move move, MoveLine counterPartMoveLine, MoveLine moveLine) {
    MoveLine taxMoveLine =
        move.getMoveLineList().stream()
            .filter(moveLineToolService::isMoveLineTaxAccount)
            .findFirst()
            .orElse(null);
    if (taxMoveLine == null) {
      return;
    }
    BigDecimal taxAmount =
        moveLine
            .getDebit()
            .max(moveLine.getCredit())
            .subtract(counterPartMoveLine.getDebit().max(counterPartMoveLine.getCredit()))
            .abs();
    if (taxMoveLine.getDebit().signum() > 0) {
      taxMoveLine.setDebit(currencyScaleService.getScaledValue(move, taxAmount));
    } else {
      taxMoveLine.setCredit(currencyScaleService.getScaledValue(move, taxAmount));
    }
  }

  protected MoveLine generateMoveLine(
      BankReconciliationLine bankReconciliationLine,
      BankStatementRule bankStatementRule,
      Move move,
      boolean isCounterpartLine,
      Account defaultAccount)
      throws AxelorException {
    MoveLine moveLine;
    LocalDate date = bankReconciliationLine.getEffectDate();
    BigDecimal debit;
    BigDecimal credit;
    LocalDate originDate =
        Optional.of(bankReconciliationLine)
            .map(BankReconciliationLine::getBankStatementLine)
            .map(BankStatementLine::getOperationDate)
            .orElse(move.getDate());
    Account account = defaultAccount;
    if (account == null) {
      account = bankReconciliationLine.getAccount();
    }
    String description = move.getDescription();
    String origin = move.getOrigin();
    Set<TaxLine> taxLineSet = new HashSet<>();
    if (isCounterpartLine) {
      debit =
          currencyScaleService.getScaledValue(
              bankReconciliationLine, bankReconciliationLine.getDebit());
      credit =
          currencyScaleService.getScaledValue(
              bankReconciliationLine, bankReconciliationLine.getCredit());

      if (account == null && bankStatementRule != null) {
        account = bankStatementRule.getCounterpartAccount();
      }
      if (account == null) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(BankPaymentExceptionMessage.BANK_STATEMENT_RULE_COUNTERPART_ACCOUNT_MISSING),
            bankStatementRule.getSearchLabel());
      }
      if (account.getIsTaxRequiredOnMoveLine()) {
        if (bankStatementRule == null || bankStatementRule.getSpecificTax() == null) {
          taxLineSet = taxService.getTaxLineSet(account.getDefaultTaxSet(), date);
        } else {
          Sets.newHashSet(taxService.getTaxLine(bankStatementRule.getSpecificTax(), date));
        }
      }
    } else {
      debit =
          currencyScaleService.getScaledValue(
              bankReconciliationLine, bankReconciliationLine.getCredit());
      credit =
          currencyScaleService.getScaledValue(
              bankReconciliationLine, bankReconciliationLine.getDebit());

      account =
          Optional.of(bankReconciliationLine)
              .map(BankReconciliationLine::getBankReconciliation)
              .map(BankReconciliation::getCashAccount)
              .orElse(null);

      if (account == null && bankStatementRule != null) {
        account = bankStatementRule.getAccountManagement().getCashAccount();
        if (account == null) {
          throw new AxelorException(
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              I18n.get(BankPaymentExceptionMessage.BANK_STATEMENT_RULE_CASH_ACCOUNT_MISSING),
              bankStatementRule.getSearchLabel());
        }
      }
    }

    boolean isDebit = debit.compareTo(credit) > 0;

    BigDecimal amount =
        currencyScaleService.getScaledValue(bankReconciliationLine, debit.add(credit));
    if (ObjectUtils.notEmpty(taxLineSet)) {
      BigDecimal taxRate = taxService.getTotalTaxRate(taxLineSet);
      amount =
          amount.divide(
              BigDecimal.ONE.add(taxRate),
              currencyScaleService.getScale(bankReconciliationLine),
              RoundingMode.HALF_UP);
    }

    moveLine =
        moveLineCreateService.createMoveLine(
            move,
            move.getPartner(),
            account,
            amount,
            isDebit,
            taxLineSet,
            date,
            move.getMoveLineList().size() + 1,
            origin,
            description);
    moveLine.setOriginDate(originDate);

    if (account.getHasAutomaticApplicationAccountingDate()) {
      moveLineService.applyCutOffDates(moveLine, move, date, date);
      moveLine.setIsCutOffGenerated(true);
    }

    if (moveLine.getAccount().getAnalyticDistributionAuthorized()) {
      if (bankReconciliationLine.getAnalyticDistributionTemplate() != null) {
        moveLine.setAnalyticDistributionTemplate(
            bankReconciliationLine.getAnalyticDistributionTemplate());
      } else if (bankStatementRule != null
          && bankStatementRule.getAnalyticDistributionTemplate() != null) {
        moveLine.setAnalyticDistributionTemplate(
            bankStatementRule.getAnalyticDistributionTemplate());
      }
    }

    if (moveLine.getAnalyticDistributionTemplate() != null) {
      moveLineComputeAnalyticService.createAnalyticDistributionWithTemplate(moveLine);
      analyticLineService.setAnalyticAccount(moveLine, move.getCompany());
    }

    move.addMoveLineListItem(moveLine);
    return moveLine;
  }

  @Override
  public void checkAccountBeforeAutoAccounting(
      BankStatementRule bankStatementRule, BankReconciliation bankReconciliation)
      throws AxelorException {
    if (bankStatementRule.getAccountManagement() != null
        && bankStatementRule.getAccountManagement().getCashAccount() != null
        && bankReconciliation.getBankDetails() != null
        && bankReconciliation.getBankDetails().getBankAccount() != null
        && !bankStatementRule
            .getAccountManagement()
            .getCashAccount()
            .equals(bankReconciliation.getBankDetails().getBankAccount())) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(BankPaymentExceptionMessage.BANK_ACCOUNT_DIFFERENT_THAN_CASH_ACCOUNT),
          bankReconciliation.getBankDetails().getIbanBic(),
          bankReconciliation.getBankDetails().getBankAccount().getCode(),
          bankStatementRule.getAccountManagement().getName(),
          bankStatementRule.getAccountManagement().getCashAccount().getCode());
    }
  }

  protected Move generateMove(
      BankReconciliation bankReconciliation,
      BankReconciliationLine bankReconciliationLine,
      BankStatementLine bankStatementLine,
      BankStatementRule bankStatementRule)
      throws AxelorException {

    Company company = null;
    Journal journal = null;
    PaymentMode paymentMode = null;
    String description = "";
    String origin = bankReconciliationLine.getReference();
    BankDetails companyBankDetails = null;
    Currency currency = null;
    FiscalPosition fiscalPosition = null;
    Partner partner = bankReconciliationLine.getPartner();
    if (bankReconciliation != null) {
      company = bankReconciliation.getCompany();
      journal = bankReconciliation.getJournal();
      description = bankReconciliation.getName();
      currency = bankReconciliation.getCurrency();
      companyBankDetails = bankReconciliation.getBankDetails();
    }
    if (bankStatementLine != null) {
      description = bankStatementLine.getDescription();
    }
    if (bankStatementRule != null) {
      if (bankStatementRule.getAccountManagement() != null) {
        AccountManagement accountManagement = bankStatementRule.getAccountManagement();

        paymentMode = accountManagement.getPaymentMode();
        if (company == null) {
          company = accountManagement.getCompany();
        }
        if (journal == null) {
          journal = accountManagement.getJournal();
        }
      }
      if (partner == null) {
        partner =
            bankStatementRuleService
                .getPartner(bankStatementRule, bankReconciliationLine)
                .orElse(null);
      }

      description =
          computeDescription(
              bankReconciliationLine,
              bankStatementLine,
              bankStatementRule.getEntryDescriptionComputation());
      origin =
          computeOrigin(
              bankReconciliationLine,
              bankStatementLine,
              bankStatementRule.getEntryOriginComputation());
    } else if (bankStatementLine != null) {
      AccountManagement accountManagement =
          accountManagementRepository
              .all()
              .filter(
                  "self.company = :company AND self.bankDetails = :bankDetails AND self.journal = :journal AND self.interbankCodeLine = :interbankCodeLine")
              .bind("company", company)
              .bind("bankDetails", companyBankDetails)
              .bind("journal", journal)
              .bind("interbankCodeLine", bankStatementLine.getOperationInterbankCodeLine())
              .fetchOne();
      if (accountManagement != null) {
        paymentMode = accountManagement.getPaymentMode();
      }
    }
    if (bankStatementLine != null && currency == null) {
      currency = bankStatementLine.getCurrency();
    }
    if (partner != null) {
      fiscalPosition = partner.getFiscalPosition();
    }

    if (journal == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(BankPaymentExceptionMessage.BANK_RECONCILIATION_CREATING_MOVE_MISSING_JOURNAL));
    }

    Move move =
        moveCreateService.createMove(
            journal,
            company,
            currency,
            partner,
            bankReconciliationLine.getEffectDate(),
            bankReconciliationLine.getEffectDate(),
            paymentMode,
            fiscalPosition,
            MoveRepository.TECHNICAL_ORIGIN_AUTOMATIC,
            MoveRepository.FUNCTIONAL_ORIGIN_PAYMENT,
            origin,
            StringHelper.cutTooLongString(description),
            companyBankDetails);
    move.setPaymentCondition(null);

    return move;
  }

  protected void manageDynamicSearchOnMoveLines(BankReconciliationLine bankReconciliationLine)
      throws AxelorException {
    if (bankReconciliationLine == null
        || bankReconciliationLine.getBankStatementLine() == null
        || bankReconciliationLine.getBankReconciliation() == null) {
      return;
    }

    List<MoveLine> fetchedMoveLineList = getMoveLineFetchedByMoveLines(bankReconciliationLine);
    MoveLine fetchedMoveLine =
        getFetchedMoveLine(fetchedMoveLineList, bankReconciliationLine.getEffectDate());

    if (fetchedMoveLine == null) {
      return;
    }
    Partner fetchedPartner = null;
    if (fetchedMoveLine.getPartner() != null
        && (fetchedMoveLineList.size() == 1
            || fetchedMoveLineList.stream()
                .noneMatch(ml -> !Objects.equals(fetchedMoveLine.getPartner(), ml.getPartner())))) {
      fetchedPartner = fetchedMoveLine.getPartner();
    }

    MoveLine counterPartMoveLine =
        generateAndFillMove(bankReconciliationLine, fetchedMoveLine, fetchedPartner);

    if (fetchedMoveLineList.size() == 1) {
      reconcileService.reconcile(counterPartMoveLine, fetchedMoveLine, false, true);
    } else {
      bankReconciliationLine.setConfidenceIndex(
          BankReconciliationLineRepository.CONFIDENCE_INDEX_ORANGE);
    }
  }

  protected MoveLine getFetchedMoveLine(List<MoveLine> fetchedMoveLineList, LocalDate date) {
    if (ObjectUtils.isEmpty(fetchedMoveLineList)) {
      return null;
    }

    MoveLine fetchedMoveLine = null;

    if (fetchedMoveLineList.size() == 1) {
      fetchedMoveLine = fetchedMoveLineList.get(0);
    } else if (date != null) {
      fetchedMoveLine =
          fetchedMoveLineList.stream()
              .sorted(
                  Comparator.comparing(
                      MoveLine::getDate, Comparator.nullsFirst(Comparator.reverseOrder())))
              .filter(ml -> ml.getDate().isBefore(date))
              .findFirst()
              .orElse(null);
    }

    return fetchedMoveLine;
  }

  protected MoveLine generateAndFillMove(
      BankReconciliationLine bankReconciliationLine,
      MoveLine fetchedMoveLine,
      Partner fetchedPartner)
      throws AxelorException {
    Move move =
        generateMove(
            bankReconciliationLine.getBankReconciliation(),
            bankReconciliationLine,
            bankReconciliationLine.getBankStatementLine(),
            null);

    move.setPartner(fetchedPartner);

    MoveLine cashMoveLine = generateMoveLine(bankReconciliationLine, null, move, false, null);

    MoveLine moveLine =
        generateMoveLine(bankReconciliationLine, null, move, true, fetchedMoveLine.getAccount());

    generateTaxMoveLine(moveLine, cashMoveLine);

    bankReconciliationLineService.reconcileBRLAndMoveLine(bankReconciliationLine, moveLine);
    bankReconciliationLine.setAccount(fetchedMoveLine.getAccount());
    bankReconciliationLine.setPartner(fetchedMoveLine.getPartner());
    bankReconciliationLine.setMoveLine(cashMoveLine);

    moveValidateService.accounting(move);

    return moveLine;
  }

  protected List<MoveLine> getMoveLineFetchedByMoveLines(
      BankReconciliationLine bankReconciliationLine) throws AxelorException {

    Company company =
        Optional.of(bankReconciliationLine)
            .map(BankReconciliationLine::getBankReconciliation)
            .map(BankReconciliation::getCompany)
            .orElse(null);
    BankDetails bankDetails =
        Optional.of(bankReconciliationLine)
            .map(BankReconciliationLine::getBankStatementLine)
            .map(BankStatementLine::getBankDetails)
            .orElse(null);
    List<MoveLine> fetchedMoveLineList = new ArrayList<>();
    List<BankStatementRule> bankStatementRuleList =
        bankStatementRuleRepository
            .all()
            .filter(
                "self.ruleTypeSelect = :ruleTypeSelect"
                    + " AND self.partnerFetchMethodSelect = :partnerFetchMethodSelect"
                    + " AND self.accountManagement.company = :company"
                    + " AND self.accountManagement.bankDetails = :bankDetails"
                    + " AND self.letterToInvoice = true")
            .bind("ruleTypeSelect", BankStatementRuleRepository.RULE_TYPE_MOVE_LINE_FETCHING)
            .bind(
                "partnerFetchMethodSelect",
                BankStatementRuleRepository.PARTNER_FETCH_METHOD_MOVE_LINE)
            .bind("company", company)
            .bind("bankDetails", bankDetails)
            .fetch();

    if (ObjectUtils.isEmpty(bankStatementRuleList)) {
      return fetchedMoveLineList;
    }

    for (BankStatementRule bankStatementRule : bankStatementRuleList) {
      MoveLine fetchedMoveLine =
          bankStatementRuleService
              .getMoveLine(bankStatementRule, bankReconciliationLine, null)
              .orElse(null);
      if (fetchedMoveLine != null) {
        fetchedMoveLineList.add(fetchedMoveLine);
      }
    }

    return fetchedMoveLineList;
  }
}
