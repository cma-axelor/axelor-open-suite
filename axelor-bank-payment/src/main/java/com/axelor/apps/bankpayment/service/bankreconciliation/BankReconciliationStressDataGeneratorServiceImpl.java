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
import com.axelor.apps.account.db.InterbankCodeLine;
import com.axelor.apps.account.db.Journal;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.repo.AccountManagementRepository;
import com.axelor.apps.account.db.repo.AccountRepository;
import com.axelor.apps.account.db.repo.AccountTypeRepository;
import com.axelor.apps.account.db.repo.InterbankCodeLineRepository;
import com.axelor.apps.account.db.repo.JournalRepository;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.account.service.move.MoveCreateService;
import com.axelor.apps.account.service.move.MoveValidateService;
import com.axelor.apps.account.service.moveline.MoveLineCreateService;
import com.axelor.apps.bankpayment.db.BankReconciliation;
import com.axelor.apps.bankpayment.db.BankStatement;
import com.axelor.apps.bankpayment.db.BankStatementFileFormat;
import com.axelor.apps.bankpayment.db.BankStatementLine;
import com.axelor.apps.bankpayment.db.BankStatementQuery;
import com.axelor.apps.bankpayment.db.BankStatementRule;
import com.axelor.apps.bankpayment.db.repo.BankReconciliationRepository;
import com.axelor.apps.bankpayment.db.repo.BankStatementFileFormatRepository;
import com.axelor.apps.bankpayment.db.repo.BankStatementLineRepository;
import com.axelor.apps.bankpayment.db.repo.BankStatementQueryRepository;
import com.axelor.apps.bankpayment.db.repo.BankStatementRepository;
import com.axelor.apps.bankpayment.db.repo.BankStatementRuleRepository;
import com.axelor.apps.bankpayment.service.bankstatement.BankStatementCreateService;
import com.axelor.apps.bankpayment.service.bankstatementline.BankStatementLineCreationService;
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.repo.BankDetailsRepository;
import com.axelor.apps.base.db.repo.CompanyRepository;
import com.axelor.apps.base.db.repo.CurrencyRepository;
import com.axelor.apps.base.db.repo.TraceBackRepository;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.common.StringUtils;
import com.axelor.db.Query;
import com.axelor.i18n.I18n;
import com.google.inject.persist.Transactional;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BankReconciliationStressDataGeneratorServiceImpl
    implements BankReconciliationStressDataGeneratorService {

  protected static final String STRESS_DATA_BANK_STATEMENT_FILE_FORMAT_NAME =
      "Stress data generator format";
  protected static final int CONFIDENCE_INDEX_GREEN = 1;
  protected static final int SCENARIO_MODE_RECONCILIATION = 1;
  protected static final int SCENARIO_MODE_AUTO_ACCOUNTING = 2;
  protected static final int GENERATION_PROFILE_STANDARD = 1;
  protected static final int GENERATION_PROFILE_WORST_CASE = 2;
  protected static final int AUTO_ACCOUNTING_MATCH_PERCENT = 80;

  protected CompanyRepository companyRepository;
  protected BankDetailsRepository bankDetailsRepository;
  protected JournalRepository journalRepository;
  protected CurrencyRepository currencyRepository;
  protected AccountRepository accountRepository;
  protected AccountManagementRepository accountManagementRepository;
  protected InterbankCodeLineRepository interbankCodeLineRepository;
  protected BankStatementQueryRepository bankStatementQueryRepository;
  protected BankStatementRuleRepository bankStatementRuleRepository;
  protected BankStatementRepository bankStatementRepository;
  protected BankStatementLineRepository bankStatementLineRepository;
  protected BankReconciliationRepository bankReconciliationRepository;
  protected BankStatementFileFormatRepository bankStatementFileFormatRepository;
  protected BankStatementLineCreationService bankStatementLineCreationService;
  protected MoveCreateService moveCreateService;
  protected MoveLineCreateService moveLineCreateService;
  protected MoveValidateService moveValidateService;
  protected BankReconciliationLoadBankStatementService bankReconciliationLoadBankStatementService;
  protected BankReconciliationBalanceComputationService bankReconciliationBalanceComputationService;
  protected AppBaseService appBaseService;
  protected BankStatementCreateService bankStatementCreateService;

  @Inject
  public BankReconciliationStressDataGeneratorServiceImpl(
      CompanyRepository companyRepository,
      BankDetailsRepository bankDetailsRepository,
      JournalRepository journalRepository,
      CurrencyRepository currencyRepository,
      AccountRepository accountRepository,
      AccountManagementRepository accountManagementRepository,
      InterbankCodeLineRepository interbankCodeLineRepository,
      BankStatementQueryRepository bankStatementQueryRepository,
      BankStatementRuleRepository bankStatementRuleRepository,
      BankStatementRepository bankStatementRepository,
      BankStatementLineRepository bankStatementLineRepository,
      BankReconciliationRepository bankReconciliationRepository,
      BankStatementFileFormatRepository bankStatementFileFormatRepository,
      BankStatementLineCreationService bankStatementLineCreationService,
      MoveCreateService moveCreateService,
      MoveLineCreateService moveLineCreateService,
      MoveValidateService moveValidateService,
      BankReconciliationLoadBankStatementService bankReconciliationLoadBankStatementService,
      BankReconciliationBalanceComputationService bankReconciliationBalanceComputationService,
      AppBaseService appBaseService) {
    this.companyRepository = companyRepository;
    this.bankDetailsRepository = bankDetailsRepository;
    this.journalRepository = journalRepository;
    this.currencyRepository = currencyRepository;
    this.accountRepository = accountRepository;
    this.accountManagementRepository = accountManagementRepository;
    this.interbankCodeLineRepository = interbankCodeLineRepository;
    this.bankStatementQueryRepository = bankStatementQueryRepository;
    this.bankStatementRuleRepository = bankStatementRuleRepository;
    this.bankStatementRepository = bankStatementRepository;
    this.bankStatementLineRepository = bankStatementLineRepository;
    this.bankReconciliationRepository = bankReconciliationRepository;
    this.bankStatementFileFormatRepository = bankStatementFileFormatRepository;
    this.bankStatementLineCreationService = bankStatementLineCreationService;
    this.moveCreateService = moveCreateService;
    this.moveLineCreateService = moveLineCreateService;
    this.moveValidateService = moveValidateService;
    this.bankReconciliationLoadBankStatementService = bankReconciliationLoadBankStatementService;
    this.bankReconciliationBalanceComputationService = bankReconciliationBalanceComputationService;
    this.appBaseService = appBaseService;
    this.bankStatementCreateService = new BankStatementCreateService();
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public String generate(
      Long companyId,
      Long bankDetailsId,
      Long journalId,
      Long currencyId,
      Long cashAccountId,
      Long counterpartAccountId,
      Integer scenarioModeSelect,
      Integer generationProfileSelect,
      Integer queryCount,
      Integer reconciliationLineCount,
      Integer workloadCount,
      String prefix)
      throws AxelorException {
    checkRequiredId(companyId, "Company");
    checkRequiredId(bankDetailsId, "Bank details");
    checkRequiredId(journalId, "Journal");
    checkRequiredId(currencyId, "Currency");
    checkRequiredId(cashAccountId, "Cash account");
    checkRequiredId(counterpartAccountId, "Counterpart account");
    checkPositiveCount(queryCount, "Query count");
    checkPositiveCount(reconciliationLineCount, "Reconciliation line count");
    checkPositiveCount(workloadCount, "Workload count");

    int scenarioMode = checkScenarioModeSelect(scenarioModeSelect);
    int generationProfile = checkGenerationProfileSelect(generationProfileSelect);
    String runKey = buildRunKey(checkPrefix(prefix));

    Company company = getRequiredModel(companyRepository.find(companyId), "Company");
    BankDetails bankDetails =
        getRequiredModel(bankDetailsRepository.find(bankDetailsId), "Bank details");
    Journal journal = getRequiredModel(journalRepository.find(journalId), "Journal");
    Currency currency = getRequiredModel(currencyRepository.find(currencyId), "Currency");
    Account cashAccount = getRequiredModel(accountRepository.find(cashAccountId), "Cash account");
    Account counterpartAccount =
        getRequiredModel(accountRepository.find(counterpartAccountId), "Counterpart account");

    checkConfiguration(bankDetails, currency, cashAccount, counterpartAccount);

    LocalDate referenceDate = appBaseService.getTodayDate(company);

    if (scenarioMode == SCENARIO_MODE_AUTO_ACCOUNTING) {
      return generateAutoAccountingScenario(
          company,
          bankDetails,
          journal,
          currency,
          cashAccount,
          counterpartAccount,
          generationProfile,
          queryCount,
          reconciliationLineCount,
          workloadCount,
          runKey,
          referenceDate);
    }

    return generateReconciliationScenario(
        company,
        bankDetails,
        journal,
        currency,
        cashAccount,
        counterpartAccount,
        generationProfile,
        queryCount,
        reconciliationLineCount,
        workloadCount,
        runKey,
        referenceDate);
  }

  protected String generateReconciliationScenario(
      Company company,
      BankDetails bankDetails,
      Journal journal,
      Currency currency,
      Account cashAccount,
      Account counterpartAccount,
      int generationProfile,
      int queryCount,
      int reconciliationLineCount,
      int candidateMoveCount,
      String runKey,
      LocalDate referenceDate)
      throws AxelorException {
    createReconciliationQueries(runKey, queryCount, generationProfile);
    createCandidateMoves(
        company,
        bankDetails,
        journal,
        currency,
        cashAccount,
        counterpartAccount,
        candidateMoveCount,
        queryCount,
        runKey,
        referenceDate);

    BankStatement bankStatement = createBankStatement(runKey, referenceDate);
    createReconciliationBankStatementLines(
        bankStatement,
        bankDetails,
        currency,
        reconciliationLineCount,
        queryCount,
        runKey,
        referenceDate);

    BankReconciliation bankReconciliation =
        createBankReconciliation(
            company, bankDetails, journal, currency, cashAccount, bankStatement, runKey);

    bankReconciliationLoadBankStatementService.loadBankStatement(bankReconciliation, true);
    bankReconciliationBalanceComputationService.computeBalances(bankReconciliation);

    return String.format(
        I18n.get(
            "Generated reconciliation stress data. Run key: %s. Profile: %s. Queries: %s. Reconciliation lines: %s. Candidate moves: %s. Bank statement id: %s. Bank reconciliation id: %s."),
        runKey,
        getGenerationProfileTitle(generationProfile),
        queryCount,
        reconciliationLineCount,
        candidateMoveCount,
        bankStatement.getId(),
        bankReconciliation.getId());
  }

  protected String generateAutoAccountingScenario(
      Company company,
      BankDetails bankDetails,
      Journal journal,
      Currency currency,
      Account cashAccount,
      Account counterpartAccount,
      int generationProfile,
      int queryCount,
      int reconciliationLineCount,
      int ruleCount,
      String runKey,
      LocalDate referenceDate)
      throws AxelorException {
    checkAutoAccountingConfiguration(bankDetails, cashAccount, queryCount, ruleCount);
    checkJournalAllowsFunctionalOrigin(journal, MoveRepository.FUNCTIONAL_ORIGIN_PAYMENT);

    InterbankCodeLine matchingInterbankCodeLine = createMatchingInterbankCodeLine(runKey);
    AccountManagement accountManagement =
        createAutoAccountingAccountManagement(
            company, bankDetails, journal, cashAccount, matchingInterbankCodeLine);

    int matchedLineCount;
    int unmatchedLineCount;

    if (isWorstCaseProfile(generationProfile)) {
      createWorstCaseAutoAccountingRules(
          runKey, queryCount, ruleCount, counterpartAccount, accountManagement);
      matchedLineCount = reconciliationLineCount;
      unmatchedLineCount = 0;
    } else {
      createAutoAccountingRules(
          runKey, queryCount, ruleCount, counterpartAccount, accountManagement);
      matchedLineCount = computeMatchedLineCount(reconciliationLineCount);
      unmatchedLineCount = reconciliationLineCount - matchedLineCount;
    }

    BankStatement bankStatement = createBankStatement(runKey, referenceDate);
    if (isWorstCaseProfile(generationProfile)) {
      createWorstCaseAutoAccountingBankStatementLines(
          bankStatement,
          bankDetails,
          currency,
          matchedLineCount,
          queryCount,
          ruleCount,
          runKey,
          referenceDate,
          matchingInterbankCodeLine);
    } else {
      createAutoAccountingBankStatementLines(
          bankStatement,
          bankDetails,
          currency,
          matchedLineCount,
          unmatchedLineCount,
          queryCount,
          runKey,
          referenceDate,
          matchingInterbankCodeLine);
    }

    BankReconciliation bankReconciliation =
        createBankReconciliation(
            company, bankDetails, journal, currency, cashAccount, bankStatement, runKey);

    bankReconciliationLoadBankStatementService.loadBankStatement(bankReconciliation, true);
    bankReconciliationBalanceComputationService.computeBalances(bankReconciliation);

    return String.format(
        I18n.get(
            "Generated auto-accounting stress data. Run key: %s. Profile: %s. Hit buckets: %s. Rules: %s. Reconciliation lines: %s. Matched lines: %s. Unmatched lines: %s. Bank statement id: %s. Bank reconciliation id: %s."),
        runKey,
        getGenerationProfileTitle(generationProfile),
        queryCount,
        ruleCount,
        reconciliationLineCount,
        matchedLineCount,
        unmatchedLineCount,
        bankStatement.getId(),
        bankReconciliation.getId());
  }

  protected void createReconciliationQueries(String runKey, int queryCount, int generationProfile) {
    int sequence = findGeneratedSequenceStart(queryCount);
    boolean worstCaseProfile = isWorstCaseProfile(generationProfile);

    for (int index = 1; index <= queryCount; index++) {
      BankStatementQuery bankStatementQuery = new BankStatementQuery();
      bankStatementQuery.setName(runKey + " - Query " + formatBucket(index));
      bankStatementQuery.setRuleTypeSelect(
          BankStatementRuleRepository.RULE_TYPE_RECONCILIATION_AUTO);
      bankStatementQuery.setConfidenceIndex(CONFIDENCE_INDEX_GREEN);
      bankStatementQuery.setSequence(sequence++);
      bankStatementQuery.setQuery(
          worstCaseProfile
              ? buildReconciliationNoMatchQuery()
              : buildReconciliationMatchingQuery(runKey, index));
      bankStatementQueryRepository.save(bankStatementQuery);
    }
  }

  protected int findGeneratedSequenceStart(int queryCount) {
    BankStatementQuery firstQuery =
        Query.of(BankStatementQuery.class)
            .filter("self.ruleTypeSelect = :ruleTypeSelect")
            .bind("ruleTypeSelect", BankStatementRuleRepository.RULE_TYPE_RECONCILIATION_AUTO)
            .order("sequence")
            .fetchOne();

    int minSequence =
        firstQuery != null && firstQuery.getSequence() != null ? firstQuery.getSequence() : 1;
    return minSequence - queryCount - 10;
  }

  protected void createCandidateMoves(
      Company company,
      BankDetails bankDetails,
      Journal journal,
      Currency currency,
      Account cashAccount,
      Account counterpartAccount,
      int candidateMoveCount,
      int queryCount,
      String runKey,
      LocalDate referenceDate)
      throws AxelorException {
    for (int index = 1; index <= candidateMoveCount; index++) {
      int queryIndex = resolveBucket(index, queryCount);
      String reference = buildReconciliationReference(runKey, queryIndex, index);
      BigDecimal amount = getAmount(index);

      Move move =
          moveCreateService.createMove(
              journal,
              company,
              currency,
              null,
              referenceDate,
              referenceDate,
              null,
              null,
              MoveRepository.TECHNICAL_ORIGIN_ENTRY,
              0,
              reference,
              reference,
              bankDetails);

      MoveLine counterpartMoveLine =
          moveLineCreateService.createMoveLine(
              move,
              null,
              counterpartAccount,
              amount,
              true,
              referenceDate,
              referenceDate,
              1,
              reference + "-CP",
              runKey + " counterpart " + index);
      move.addMoveLineListItem(counterpartMoveLine);

      MoveLine cashMoveLine =
          moveLineCreateService.createMoveLine(
              move,
              null,
              cashAccount,
              amount,
              false,
              referenceDate,
              referenceDate,
              2,
              reference,
              reference);
      move.addMoveLineListItem(cashMoveLine);

      moveValidateService.accounting(move);
    }
  }

  protected void createReconciliationBankStatementLines(
      BankStatement bankStatement,
      BankDetails bankDetails,
      Currency currency,
      int reconciliationLineCount,
      int queryCount,
      String runKey,
      LocalDate referenceDate) {
    for (int index = 1; index <= reconciliationLineCount; index++) {
      int queryIndex = resolveBucket(index, queryCount);
      String reference = buildReconciliationReference(runKey, queryIndex, index);

      BankStatementLine bankStatementLine =
          bankStatementLineCreationService.createBankStatementLine(
              bankStatement,
              index,
              bankDetails,
              getAmount(index),
              BigDecimal.ZERO,
              currency,
              reference,
              referenceDate,
              referenceDate,
              null,
              null,
              reference,
              reference);
      bankStatementLineRepository.save(bankStatementLine);
    }
  }

  protected InterbankCodeLine createMatchingInterbankCodeLine(String runKey) {
    InterbankCodeLine interbankCodeLine = new InterbankCodeLine();
    interbankCodeLine.setCode(runKey + "-MATCH");
    interbankCodeLine.setName(runKey + " Match");
    interbankCodeLine.setDescription("Temporary auto-accounting stress data key");
    interbankCodeLineRepository.save(interbankCodeLine);
    return interbankCodeLine;
  }

  protected AccountManagement createAutoAccountingAccountManagement(
      Company company,
      BankDetails bankDetails,
      Journal journal,
      Account cashAccount,
      InterbankCodeLine interbankCodeLine) {
    AccountManagement accountManagement = new AccountManagement();
    accountManagement.setCompany(company);
    accountManagement.setBankDetails(bankDetails);
    accountManagement.setJournal(journal);
    accountManagement.setCashAccount(cashAccount);
    accountManagement.setInterbankCodeLine(interbankCodeLine);
    accountManagementRepository.save(accountManagement);
    return accountManagement;
  }

  protected void createAutoAccountingRules(
      String runKey,
      int queryCount,
      int ruleCount,
      Account counterpartAccount,
      AccountManagement accountManagement) {
    List<Integer> rulesPerBucket = computeRulesPerBucket(queryCount, ruleCount);

    for (int bucketIndex = 1; bucketIndex <= queryCount; bucketIndex++) {
      int bucketRuleCount = rulesPerBucket.get(bucketIndex - 1);
      for (int position = 1; position <= bucketRuleCount; position++) {
        boolean matchingRule = position == bucketRuleCount;

        BankStatementQuery bankStatementQuery = new BankStatementQuery();
        bankStatementQuery.setName(
            String.format("%s - Auto rule B%s-R%03d", runKey, formatBucket(bucketIndex), position));
        bankStatementQuery.setRuleTypeSelect(BankStatementRuleRepository.RULE_TYPE_ACCOUNTING_AUTO);
        bankStatementQuery.setQuery(buildAutoAccountingQuery());
        bankStatementQueryRepository.save(bankStatementQuery);

        BankStatementRule bankStatementRule = new BankStatementRule();
        bankStatementRule.setAccountManagement(accountManagement);
        bankStatementRule.setCounterpartAccount(counterpartAccount);
        bankStatementRule.setBankStatementQuery(bankStatementQuery);
        bankStatementRule.setRuleTypeSelect(BankStatementRuleRepository.RULE_TYPE_ACCOUNTING_AUTO);
        bankStatementRule.setSearchLabel(
            matchingRule
                ? buildAutoAccountingHitToken(runKey, bucketIndex)
                : buildAutoAccountingFalseToken(runKey, bucketIndex, position));
        bankStatementRule.setLetterToInvoice(false);
        bankStatementRuleRepository.save(bankStatementRule);
      }
    }
  }

  protected void createWorstCaseAutoAccountingRules(
      String runKey,
      int queryCount,
      int ruleCount,
      Account counterpartAccount,
      AccountManagement accountManagement) {
    for (int position = 1; position <= ruleCount; position++) {
      int bucketIndex = resolveBucket(position, queryCount);
      boolean matchingRule = position == ruleCount;

      BankStatementQuery bankStatementQuery = new BankStatementQuery();
      bankStatementQuery.setName(
          String.format(
              "%s - Worst auto rule B%s-R%03d", runKey, formatBucket(bucketIndex), position));
      bankStatementQuery.setRuleTypeSelect(BankStatementRuleRepository.RULE_TYPE_ACCOUNTING_AUTO);
      bankStatementQuery.setQuery(buildAutoAccountingQuery());
      bankStatementQueryRepository.save(bankStatementQuery);

      BankStatementRule bankStatementRule = new BankStatementRule();
      bankStatementRule.setAccountManagement(accountManagement);
      bankStatementRule.setCounterpartAccount(counterpartAccount);
      bankStatementRule.setBankStatementQuery(bankStatementQuery);
      bankStatementRule.setRuleTypeSelect(BankStatementRuleRepository.RULE_TYPE_ACCOUNTING_AUTO);
      bankStatementRule.setSearchLabel(
          matchingRule
              ? buildAutoAccountingHitToken(runKey, bucketIndex)
              : buildAutoAccountingFalseToken(runKey, bucketIndex, position));
      bankStatementRule.setLetterToInvoice(false);
      bankStatementRuleRepository.save(bankStatementRule);
    }
  }

  protected List<Integer> computeRulesPerBucket(int queryCount, int ruleCount) {
    List<Integer> rulesPerBucket = new ArrayList<>();
    int baseRuleCount = ruleCount / queryCount;
    int remainder = ruleCount % queryCount;

    for (int bucketIndex = 1; bucketIndex <= queryCount; bucketIndex++) {
      rulesPerBucket.add(baseRuleCount + (bucketIndex <= remainder ? 1 : 0));
    }

    return rulesPerBucket;
  }

  protected void createAutoAccountingBankStatementLines(
      BankStatement bankStatement,
      BankDetails bankDetails,
      Currency currency,
      int matchedLineCount,
      int unmatchedLineCount,
      int queryCount,
      String runKey,
      LocalDate referenceDate,
      InterbankCodeLine matchingInterbankCodeLine) {
    for (int index = 1; index <= matchedLineCount; index++) {
      int bucketIndex = resolveBucket(index, queryCount);
      String reference = buildAutoAccountingHitReference(runKey, bucketIndex, index);
      BankStatementLine bankStatementLine =
          bankStatementLineCreationService.createBankStatementLine(
              bankStatement,
              index,
              bankDetails,
              getAmount(index),
              BigDecimal.ZERO,
              currency,
              reference,
              referenceDate,
              referenceDate,
              matchingInterbankCodeLine,
              null,
              reference,
              reference);
      bankStatementLineRepository.save(bankStatementLine);
    }

    for (int index = 1; index <= unmatchedLineCount; index++) {
      int sequence = matchedLineCount + index;
      String reference = buildAutoAccountingMissReference(runKey, index);
      BankStatementLine bankStatementLine =
          bankStatementLineCreationService.createBankStatementLine(
              bankStatement,
              sequence,
              null,
              getAmount(sequence),
              BigDecimal.ZERO,
              currency,
              reference,
              referenceDate,
              referenceDate,
              null,
              null,
              reference,
              reference);
      bankStatementLineRepository.save(bankStatementLine);
    }
  }

  protected void createWorstCaseAutoAccountingBankStatementLines(
      BankStatement bankStatement,
      BankDetails bankDetails,
      Currency currency,
      int reconciliationLineCount,
      int queryCount,
      int ruleCount,
      String runKey,
      LocalDate referenceDate,
      InterbankCodeLine matchingInterbankCodeLine) {
    int matchingBucketIndex = resolveBucket(ruleCount, queryCount);

    for (int index = 1; index <= reconciliationLineCount; index++) {
      String reference = buildAutoAccountingHitReference(runKey, matchingBucketIndex, index);
      BankStatementLine bankStatementLine =
          bankStatementLineCreationService.createBankStatementLine(
              bankStatement,
              index,
              bankDetails,
              getAmount(index),
              BigDecimal.ZERO,
              currency,
              reference,
              referenceDate,
              referenceDate,
              matchingInterbankCodeLine,
              null,
              reference,
              reference);
      bankStatementLineRepository.save(bankStatementLine);
    }
  }

  protected int computeMatchedLineCount(int reconciliationLineCount) {
    if (reconciliationLineCount <= 1) {
      return reconciliationLineCount;
    }

    int matchedLineCount =
        (int) Math.ceil((reconciliationLineCount * AUTO_ACCOUNTING_MATCH_PERCENT) / 100.0);
    matchedLineCount = Math.max(1, matchedLineCount);
    return Math.min(reconciliationLineCount - 1, matchedLineCount);
  }

  protected BankStatement createBankStatement(String runKey, LocalDate referenceDate) {
    BankStatement bankStatement = new BankStatement();
    bankStatement.setBankStatementFileFormat(getOrCreateBankStatementFileFormat());
    bankStatement.setFromDate(referenceDate);
    bankStatement.setToDate(referenceDate);
    bankStatement.setStatusSelect(BankStatementRepository.STATUS_IMPORTED);
    bankStatement.setName(
        String.format("%s - %s", runKey, bankStatementCreateService.computeName(bankStatement)));
    bankStatementRepository.save(bankStatement);
    return bankStatement;
  }

  protected BankReconciliation createBankReconciliation(
      Company company,
      BankDetails bankDetails,
      Journal journal,
      Currency currency,
      Account cashAccount,
      BankStatement bankStatement,
      String runKey) {
    BankReconciliation bankReconciliation = new BankReconciliation();
    bankReconciliation.setName(runKey + " - Reconciliation");
    bankReconciliation.setCompany(company);
    bankReconciliation.setBankDetails(bankDetails);
    bankReconciliation.setJournal(journal);
    bankReconciliation.setCurrency(currency);
    bankReconciliation.setCashAccount(cashAccount);
    bankReconciliation.setBankStatement(bankStatement);
    bankReconciliation.setFromDate(bankStatement.getFromDate());
    bankReconciliation.setToDate(bankStatement.getToDate());
    bankReconciliation.setIncludeOtherBankStatements(false);
    bankReconciliation.setAccountBalance(BigDecimal.ZERO);
    bankReconciliation.setStartingBalance(BigDecimal.ZERO);
    bankReconciliationRepository.save(bankReconciliation);
    return bankReconciliation;
  }

  protected BankStatementFileFormat getOrCreateBankStatementFileFormat() {
    BankStatementFileFormat bankStatementFileFormat =
        bankStatementFileFormatRepository.findByName(STRESS_DATA_BANK_STATEMENT_FILE_FORMAT_NAME);
    if (bankStatementFileFormat != null) {
      return bankStatementFileFormat;
    }

    bankStatementFileFormat = new BankStatementFileFormat();
    bankStatementFileFormat.setName(STRESS_DATA_BANK_STATEMENT_FILE_FORMAT_NAME);
    bankStatementFileFormat.setStatementFileFormatSelect(
        BankStatementFileFormatRepository.FILE_FORMAT_CSV_YMD_DOT);
    bankStatementFileFormatRepository.save(bankStatementFileFormat);
    return bankStatementFileFormat;
  }

  protected int checkScenarioModeSelect(Integer scenarioModeSelect) throws AxelorException {
    if (scenarioModeSelect == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_MISSING_FIELD, I18n.get("Scenario is required."));
    }

    if (scenarioModeSelect != SCENARIO_MODE_RECONCILIATION
        && scenarioModeSelect != SCENARIO_MODE_AUTO_ACCOUNTING) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get("Unsupported stress data scenario."));
    }

    return scenarioModeSelect;
  }

  protected int checkGenerationProfileSelect(Integer generationProfileSelect)
      throws AxelorException {
    if (generationProfileSelect == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_MISSING_FIELD, I18n.get("Profile is required."));
    }

    if (generationProfileSelect != GENERATION_PROFILE_STANDARD
        && generationProfileSelect != GENERATION_PROFILE_WORST_CASE) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY, I18n.get("Unsupported stress data profile."));
    }

    return generationProfileSelect;
  }

  protected void checkConfiguration(
      BankDetails bankDetails, Currency currency, Account cashAccount, Account counterpartAccount)
      throws AxelorException {
    if (bankDetails.getCurrency() != null && !bankDetails.getCurrency().equals(currency)) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get("Bank details currency must match the selected currency."));
    }

    if (cashAccount.getAccountType() == null
        || !cashAccount
            .getAccountType()
            .getTechnicalTypeSelect()
            .equals(AccountTypeRepository.TYPE_CASH)) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get("Cash account must use the cash account type."));
    }

    if (cashAccount.equals(counterpartAccount)) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get("Cash account and counterpart account must be different."));
    }
  }

  protected void checkAutoAccountingConfiguration(
      BankDetails bankDetails, Account cashAccount, int queryCount, int ruleCount)
      throws AxelorException {
    if (ruleCount < queryCount) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get("Workload count must be greater than or equal to query count."));
    }

    if (bankDetails.getBankAccount() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get("Bank details must define a bank account for auto-accounting stress data."));
    }

    if (!bankDetails.getBankAccount().equals(cashAccount)) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(
              "For auto-accounting stress data, selected cash account must match bank details bank account."));
    }
  }

  protected void checkJournalAllowsFunctionalOrigin(Journal journal, int functionalOriginSelect)
      throws AxelorException {
    if (journal == null || StringUtils.isBlank(journal.getAuthorizedFunctionalOriginSelect())) {
      return;
    }

    for (String authorizedFunctionalOrigin :
        journal.getAuthorizedFunctionalOriginSelect().split(",")) {
      if (String.valueOf(functionalOriginSelect).equals(authorizedFunctionalOrigin.trim())) {
        return;
      }
    }

    throw new AxelorException(
        TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
        I18n.get(
            "Selected journal does not allow Payment functional origin. Please choose a journal that authorizes Payment or clear the journal restriction."));
  }

  protected String checkPrefix(String prefix) throws AxelorException {
    if (StringUtils.isBlank(prefix)) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_MISSING_FIELD, I18n.get("Prefix is required."));
    }

    return prefix.trim();
  }

  protected String buildRunKey(String prefix) {
    String sanitizedPrefix = prefix.replaceAll("[^A-Za-z0-9_-]+", "-").replaceAll("^-+|-+$", "");
    if (sanitizedPrefix.isEmpty()) {
      sanitizedPrefix = "STRESS";
    }
    return sanitizedPrefix.toUpperCase(Locale.ROOT) + "-" + System.currentTimeMillis();
  }

  protected void checkRequiredId(Long id, String fieldName) throws AxelorException {
    if (id == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          String.format(I18n.get("%s is required."), fieldName));
    }
  }

  protected void checkPositiveCount(Integer count, String fieldName) throws AxelorException {
    if (count == null || count <= 0) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          String.format(I18n.get("%s must be greater than zero."), fieldName));
    }
  }

  protected <T> T getRequiredModel(T model, String fieldName) throws AxelorException {
    if (model == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          String.format(I18n.get("%s is required."), fieldName));
    }
    return model;
  }

  protected BigDecimal getAmount(int index) {
    return BigDecimal.valueOf(1000L + index);
  }

  protected int resolveBucket(int index, int bucketCount) {
    return ((index - 1) % bucketCount) + 1;
  }

  protected String buildReconciliationReference(String runKey, int queryIndex, int index) {
    return String.format("%s-Q%s-I%04d", runKey, formatBucket(queryIndex), index);
  }

  protected String buildReconciliationMatchingQuery(String runKey, int queryIndex) {
    return String.format(
        "reference?.startsWith('%s-Q%s-') && moveLine?.origin == reference && moveLine?.currencyAmount.abs() == currencyAmount",
        escapeGroovyString(runKey), formatBucket(queryIndex));
  }

  protected String buildReconciliationNoMatchQuery() {
    return "false";
  }

  protected String buildAutoAccountingQuery() {
    return "reference?.contains(%s) || description?.contains(%s) || origin?.contains(%s)";
  }

  protected String buildAutoAccountingHitToken(String runKey, int bucketIndex) {
    return String.format("%s-HIT-B%s", runKey, formatBucket(bucketIndex));
  }

  protected String buildAutoAccountingFalseToken(String runKey, int bucketIndex, int position) {
    return String.format("%s-FALSE-B%s-R%03d", runKey, formatBucket(bucketIndex), position);
  }

  protected String buildAutoAccountingHitReference(String runKey, int bucketIndex, int index) {
    return String.format(
        "%s-L%04d-%s", runKey, index, buildAutoAccountingHitToken(runKey, bucketIndex));
  }

  protected String buildAutoAccountingMissReference(String runKey, int index) {
    return String.format("%s-L%04d-MISS", runKey, index);
  }

  protected String formatBucket(int queryIndex) {
    return String.format("%02d", queryIndex);
  }

  protected boolean isWorstCaseProfile(int generationProfile) {
    return generationProfile == GENERATION_PROFILE_WORST_CASE;
  }

  protected String getGenerationProfileTitle(int generationProfile) {
    return isWorstCaseProfile(generationProfile) ? "Worst-case" : "Standard";
  }

  protected String escapeGroovyString(String value) {
    return value.replace("\\", "\\\\").replace("'", "\\'");
  }
}
