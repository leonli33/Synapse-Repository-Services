package org.sagebionetworks.table.query;

import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sagebionetworks.repo.model.table.SortDirection;
import org.sagebionetworks.repo.model.table.SortItem;
import org.sagebionetworks.table.query.model.ComparisonPredicate;
import org.sagebionetworks.table.query.model.DerivedColumn;
import org.sagebionetworks.table.query.model.ExactNumericLiteral;
import org.sagebionetworks.table.query.model.OrderByClause;
import org.sagebionetworks.table.query.model.Pagination;
import org.sagebionetworks.table.query.model.QuerySpecification;
import org.sagebionetworks.table.query.model.SortKey;
import org.sagebionetworks.table.query.model.UnsignedLiteral;
import org.sagebionetworks.table.query.model.UnsignedNumericLiteral;
import org.sagebionetworks.table.query.model.UnsignedValueSpecification;
import org.sagebionetworks.table.query.model.WhereClause;
import org.sagebionetworks.table.query.util.SimpleAggregateQueryException;
import org.sagebionetworks.table.query.util.SqlElementUtils;

import com.google.common.collect.Lists;

public class SqlElementUtilsTest {

	String searchConditionString;
	StringBuilder stringBuilder;
	WhereClause whereClause;

	@BeforeEach
	public void setUp() throws ParseException {
		whereClause = new WhereClause(SqlElementUtils.createSearchCondition("water=wet AND sky=blue"));
		searchConditionString = "(tabs > spaces)";
		stringBuilder = new StringBuilder();
	}

	@Test
	public void testConvertToSorted() throws ParseException {
		OrderByClause model = new TableQueryParser("order by bar").orderByClause();
		SortItem sort1 = new SortItem();
		sort1.setColumn("foo");
		SortItem sort2 = new SortItem();
		sort2.setColumn("zoo");
		sort2.setDirection(SortDirection.ASC);
		SortItem sort3 = new SortItem();
		sort3.setColumn("zaa");
		sort3.setDirection(SortDirection.DESC);
		OrderByClause converted = SqlElementUtils.convertToSortedQuery(model, Lists.newArrayList(sort1, sort2, sort3));
		assertNotNull(converted);
		assertEquals("ORDER BY \"foo\" ASC, \"zoo\" ASC, \"zaa\" DESC, bar", converted.toString());
	}

	@Test
	public void testConvertToSortedEscaped() throws ParseException {
		OrderByClause model = new TableQueryParser("order by bar").orderByClause();
		SortItem sort1 = new SortItem();
		sort1.setColumn("foo-bar");
		SortItem sort2 = new SortItem();
		sort2.setColumn("zoo");
		sort2.setDirection(SortDirection.ASC);
		SortItem sort3 = new SortItem();
		sort3.setColumn("zaa");
		sort3.setDirection(SortDirection.DESC);
		OrderByClause converted = SqlElementUtils.convertToSortedQuery(model, Lists.newArrayList(sort1, sort2, sort3));
		assertNotNull(converted);
		assertEquals("ORDER BY \"foo-bar\" ASC, \"zoo\" ASC, \"zaa\" DESC, bar", converted.toString());
	}

	@Test
	public void testConvertToSortedPLFM_4118() throws ParseException {
		OrderByClause model = null;
		SortItem sort1 = new SortItem();
		sort1.setColumn("First Name");
		OrderByClause converted = SqlElementUtils.convertToSortedQuery(model, Lists.newArrayList(sort1));
		assertNotNull(converted);
		assertEquals("ORDER BY \"First Name\" ASC", converted.toString());
	}

	@Test
	public void testReplaceSorted() throws ParseException {
		OrderByClause model = new TableQueryParser("order by bar").orderByClause();
		SortItem sort1 = new SortItem();
		sort1.setColumn("bar");
		sort1.setDirection(SortDirection.DESC);
		SortItem sort2 = new SortItem();
		sort2.setColumn("foo");
		OrderByClause converted = SqlElementUtils.convertToSortedQuery(model, Lists.newArrayList(sort1, sort2));
		assertNotNull(converted);
		assertEquals("ORDER BY \"bar\" DESC, \"foo\" ASC", converted.toString());
	}

	@Test
	public void testConvertToSortedQuerySortWithDoubleQuote() throws ParseException {
		OrderByClause model = null;
		SortItem sort1 = new SortItem();
		sort1.setColumn("has\"Quote");
		OrderByClause converted = SqlElementUtils.convertToSortedQuery(model, Lists.newArrayList(sort1));
		assertNotNull(converted);
		assertEquals("ORDER BY \"has\"\"Quote\" ASC", converted.toString());
	}

	@Test
	public void testOverridePaginationQueryWithoutPagingOverridesNull() throws ParseException {
		Pagination model = null;
		Long limit = null;
		Long offset = null;
		Pagination converted = SqlElementUtils.overridePagination(model, offset, limit);
		assertNull(converted);
	}

	@Test
	public void testOverridePaginationQueryWithoutPagingOverrideOffsetNull() throws ParseException {
		Pagination model = null;
		Long limit = 15L;
		Long offset = null;
		Pagination converted = SqlElementUtils.overridePagination(model, offset, limit);
		assertEquals("LIMIT 15 OFFSET 0", converted.toString());
	}

	@Test
	public void testOverridePaginationQueryWithLimitOffsetOverridesNull() throws ParseException {
		Pagination model = new TableQueryParser("limit 34 offset 12").pagination();
		Long limit = null;
		Long offset = null;
		Pagination converted = SqlElementUtils.overridePagination(model, offset, limit);
		assertEquals("LIMIT 34 OFFSET 12", converted.toString());
	}

	@Test
	public void testOverridePaginationQueryWithLimitOffsetOverrideLimitNull() throws ParseException {
		Pagination model = new TableQueryParser("limit 34 offset 12").pagination();
		Long limit = null;
		Long offset = 2L;
		Pagination converted = SqlElementUtils.overridePagination(model, offset, limit);
		assertEquals("LIMIT 32 OFFSET 14", converted.toString());
	}

	@Test
	public void testOverridePaginationQueryWithLimitOffsetOverrideOffsetNull() throws ParseException {
		Pagination model = new TableQueryParser("limit 34 offset 12").pagination();
		Long limit = 3L;
		Long offset = null;
		Pagination converted = SqlElementUtils.overridePagination(model, offset, limit);
		assertEquals("LIMIT 3 OFFSET 12", converted.toString());
	}

	@Test
	public void testOverridePaginationQueryQueryLimitGreaterThanOverrideOffset() throws ParseException {
		Pagination model = new TableQueryParser("limit 100 offset 50").pagination();
		Long limit = 25L;
		Long offset = 10L;
		Pagination converted = SqlElementUtils.overridePagination(model, offset, limit);
		assertEquals("LIMIT 25 OFFSET 60", converted.toString());
	}

	@Test
	public void testOverridePaginationQueryQueryLimitLessThanOverrideOffset() throws ParseException {
		Pagination model = new TableQueryParser("limit 100 offset 50").pagination();
		Long limit = 25L;
		Long offset = 101L;
		Pagination converted = SqlElementUtils.overridePagination(model, offset, limit);
		assertEquals("LIMIT 0 OFFSET 151", converted.toString());
	}

	@Test
	public void testOverridePaginationMaxRowPerPageNull() throws ParseException {
		Pagination model = new TableQueryParser("limit 100 offset 75").pagination();
		Long limit = 50L;
		Long offset = 10L;
		Pagination converted = SqlElementUtils.overridePagination(model, offset, limit);
		assertEquals("LIMIT 50 OFFSET 85", converted.toString());
	}

	@Test
	public void testOverridePaginationAllNull() throws ParseException {
		Pagination model = new TableQueryParser("limit 100 offset 75").pagination();
		Long limit = null;
		Long offset = null;
		Pagination converted = SqlElementUtils.overridePagination(model, offset, limit);
		assertEquals("LIMIT 100 OFFSET 75", converted.toString());
	}
	
	@Test
	public void testLimitMaxRowsPerPageWithUnderMax() throws ParseException {
		Pagination model = new TableQueryParser("limit 100 offset 75").pagination();
		Long maxRowsPerPage = 101L;
		// call under test
		Pagination converted = SqlElementUtils.limitMaxRowsPerPage(model, maxRowsPerPage);
		assertEquals("LIMIT 100 OFFSET 75", converted.toString());
	}
	
	@Test
	public void testLimitMaxRowsPerPageWithUnderWithAtMax() throws ParseException {
		Pagination model = new TableQueryParser("limit 100 offset 75").pagination();
		Long maxRowsPerPage = 100L;
		// call under test
		Pagination converted = SqlElementUtils.limitMaxRowsPerPage(model, maxRowsPerPage);
		assertEquals("LIMIT 100 OFFSET 75", converted.toString());
	}

	@Test
	public void testLimitMaxRowsPerPageWithUnderWithOverMax() throws ParseException {
		Pagination model = new TableQueryParser("limit 100 offset 75").pagination();
		Long maxRowsPerPage = 99L;
		// call under test
		Pagination converted = SqlElementUtils.limitMaxRowsPerPage(model, maxRowsPerPage);
		assertEquals("LIMIT 99 OFFSET 75", converted.toString());
	}
	
	@Test
	public void testLimitMaxRowsPerPageWithUnderWithNullMax() throws ParseException {
		Pagination model = new TableQueryParser("limit 100 offset 75").pagination();
		Long maxRowsPerPage = null;
		// call under test
		Pagination converted = SqlElementUtils.limitMaxRowsPerPage(model, maxRowsPerPage);
		assertEquals("LIMIT 100 OFFSET 75", converted.toString());
	}
	
	@Test
	public void testLimitMaxRowsPerPageWithUnderWithNullPage() throws ParseException {
		Pagination model = null;
		Long maxRowsPerPage = 101L;
		// call under test
		Pagination converted = SqlElementUtils.limitMaxRowsPerPage(model, maxRowsPerPage);
		assertEquals("LIMIT 101 OFFSET 0", converted.toString());
	}
	
	@Test
	public void testLimitMaxRowsPerPageWithUnderWithNullOffestAndOverMax() throws ParseException {
		Pagination model = new TableQueryParser("limit 100").pagination();
		Long maxRowsPerPage = 99L;
		// call under test
		Pagination converted = SqlElementUtils.limitMaxRowsPerPage(model, maxRowsPerPage);
		assertEquals("LIMIT 99", converted.toString());
	}
	
	@Test
	public void testEntityIdRightHandSide() throws ParseException {
		QuerySpecification model = TableQueryParser.parserQuery("select * from syn123 where id = syn456");
		assertEquals("syn123", model.getSingleTableName().get());
		ComparisonPredicate predicate = model.getFirstElementOfType(ComparisonPredicate.class);
		assertEquals("id", predicate.getLeftHandSide().toSql());
		assertEquals("syn456", predicate.getRowValueConstructorRHS().toSqlWithoutQuotes());
	}

	@Test
	public void testEntityIdRightHandSideSingeQuotes() throws ParseException {
		QuerySpecification model = TableQueryParser.parserQuery("select * from syn123 where id = 'syn456'");
		assertEquals("syn123", model.getSingleTableName().get());
		ComparisonPredicate predicate = model.getFirstElementOfType(ComparisonPredicate.class);
		assertEquals("id", predicate.getLeftHandSide().toSql());
		assertEquals("syn456", predicate.getRowValueConstructorRHS().toSqlWithoutQuotes());
	}

	@Test
	public void testEntityIdRightHandSideDoubleQuotes() throws ParseException {
		QuerySpecification model = TableQueryParser.parserQuery("select * from syn123 where id = \"syn456\"");
		assertEquals("syn123", model.getSingleTableName().get());
		ComparisonPredicate predicate = model.getFirstElementOfType(ComparisonPredicate.class);
		assertEquals("id", predicate.getLeftHandSide().toSql());
		assertEquals("syn456", predicate.getRowValueConstructorRHS().toSqlWithoutQuotes());
	}

	@Test
	public void testCreateCountSqlNonAggregate() throws ParseException, SimpleAggregateQueryException {
		QuerySpecification model = new TableQueryParser(
				"select * from syn123 where bar < 1.0 order by foo, bar limit 2 offset 5").querySpecification();
		assertTrue(SqlElementUtils.createCountSql(model));
		assertEquals("SELECT COUNT(*) FROM syn123 WHERE bar < 1.0", model.toSql());
	}

	@Test
	public void testCreateCountSqlGroupBy() throws ParseException, SimpleAggregateQueryException {
		QuerySpecification model = new TableQueryParser("select foo, bar, count(*) from syn123 group by foo, bar")
				.querySpecification();
		assertTrue(SqlElementUtils.createCountSql(model));
		assertEquals("SELECT COUNT(DISTINCT foo, bar) FROM syn123", model.toSql());
	}

	@Test
	public void testCreateCountSqlDistinct() throws ParseException, SimpleAggregateQueryException {
		QuerySpecification model = new TableQueryParser("select distinct foo, bar from syn123").querySpecification();
		assertTrue(SqlElementUtils.createCountSql(model));
		assertEquals("SELECT COUNT(DISTINCT foo, bar) FROM syn123", model.toSql());
	}

	@Test
	public void testCreateCountSimpleAggregateCountStar() throws ParseException, SimpleAggregateQueryException {
		QuerySpecification model = new TableQueryParser("select count(*) from syn123").querySpecification();
		assertFalse(SqlElementUtils.createCountSql(model));
	}

	@Test
	public void testCreateCountSimpleAggregateMultipleAggregate() throws ParseException, SimpleAggregateQueryException {
		QuerySpecification model = new TableQueryParser("select sum(foo), max(bar) from syn123").querySpecification();
		assertFalse(SqlElementUtils.createCountSql(model));
	}

	@Test
	public void testBuildSqlSelectRowIds() throws ParseException, SimpleAggregateQueryException {
		long limit = 100;
		QuerySpecification model = new TableQueryParser("select * from T123 WHERE _C2_ = 'BAR' ORDER BY _C1_")
				.querySpecification();
		// call under test
		String countSql = SqlElementUtils.buildSqlSelectRowIdAndVersions(model, limit).get();
		assertEquals("SELECT ROW_ID, ROW_VERSION FROM T123 WHERE _C2_ = 'BAR' LIMIT 100", countSql);
	}

	@Test
	public void testBuildSqlSelectRowIdsInputWithLimit() throws ParseException, SimpleAggregateQueryException {
		long limit = 100;
		QuerySpecification model = new TableQueryParser("select * from T123 limit 200 offset 100").querySpecification();
		// call under test
		String countSql = SqlElementUtils.buildSqlSelectRowIdAndVersions(model, limit).get();
		assertEquals("SELECT ROW_ID, ROW_VERSION FROM T123 LIMIT 100", countSql);
	}

	@Test
	public void testBuildSqlSelectRowIdsInputWithAggregate() throws ParseException, SimpleAggregateQueryException {
		long limit = 100;
		QuerySpecification model = new TableQueryParser("select count(*) from T123").querySpecification();
		// call under test
		assertEquals(Optional.empty(), SqlElementUtils.buildSqlSelectRowIdAndVersions(model, limit));
	}

	@Test
	public void testCreateSelectFromGroupBy() throws Exception {
		QuerySpecification model = new TableQueryParser("select foo as a, bar from syn123 group by bar, a")
				.querySpecification();
		String result = SqlElementUtils.createSelectFromGroupBy(model.getSelectList(),
				model.getTableExpression().getGroupByClause());
		assertEquals("bar, foo", result);
	}

	@Test
	public void testCreateSelectFromGroupBySingleQuotes() throws Exception {
		QuerySpecification model = new TableQueryParser("select 'has space' as b from syn123 group by b")
				.querySpecification();
		String result = SqlElementUtils.createSelectFromGroupBy(model.getSelectList(),
				model.getTableExpression().getGroupByClause());
		assertEquals("'has space'", result);
	}

	@Test
	public void testCreateSelectFromGroupByDoubleQuotes() throws Exception {
		QuerySpecification model = new TableQueryParser("select \"has space\" as b from syn123 group by b")
				.querySpecification();
		String result = SqlElementUtils.createSelectFromGroupBy(model.getSelectList(),
				model.getTableExpression().getGroupByClause());
		assertEquals("\"has space\"", result);
	}

	/**
	 * This is a test for PLFM-3899.
	 */
	@Test
	public void testCreateCountSqlAsInGroupBy() throws Exception {
		QuerySpecification model = new TableQueryParser("select foo as a from syn123 group by a").querySpecification();
		assertTrue(SqlElementUtils.createCountSql(model));
		assertEquals("SELECT COUNT(DISTINCT foo) FROM syn123", model.toSql());
	}

	@Test
	public void testCreateSelectWithoutAs() throws Exception {
		QuerySpecification model = new TableQueryParser("select foo as a, bar as boo from syn123").querySpecification();
		String countSql = SqlElementUtils.createSelectWithoutAs(model.getSelectList());
		assertEquals("foo, bar", countSql);
	}

	@Test
	public void testCreateSelectWithoutAsQuote() throws Exception {
		QuerySpecification model = new TableQueryParser("select 'foo' as a, \"bar\" as boo from syn123")
				.querySpecification();
		String countSql = SqlElementUtils.createSelectWithoutAs(model.getSelectList());
		assertEquals("'foo', \"bar\"", countSql);
	}

	/**
	 * This is a test for PLFM-3900.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testCreateCountSqlDistinctWithAs() throws Exception {
		QuerySpecification model = new TableQueryParser("select distinct foo as a from syn123").querySpecification();
		assertTrue(SqlElementUtils.createCountSql(model));
		assertEquals("SELECT COUNT(DISTINCT foo) FROM syn123", model.toSql());
	}

	@Test
	public void testWrapInDoubleQuotes() {
		String toWrap = "foo";
		String result = SqlElementUtils.wrapInDoubleQuotes(toWrap);
		assertEquals("\"foo\"", result);
	}

	@Test
	public void testWrapInDoubleQuotes_StringContainsDoubleQuotes() {
		String toBeWrapped = "\"Don't trust everything you read on the internet\" - Abraham Lincoln";
		String expectedResult = "\"\"\"Don't trust everything you read on the internet\"\" - Abraham Lincoln\"";
		assertEquals(expectedResult, SqlElementUtils.wrapInDoubleQuotes(toBeWrapped));
	}

	@Test
	public void testCreateSortKeyWithKeywordInName() throws ParseException {
		SortKey sortKey = SqlElementUtils.createSortKey("Date Approved/Rejected");
		assertNotNull(sortKey);
		assertEquals("\"Date Approved/Rejected\"", sortKey.toSql());
	}

	@Test
	public void testCreateSortKeySpace() throws ParseException {
		SortKey sortKey = SqlElementUtils.createSortKey("First Name");
		assertNotNull(sortKey);
		assertEquals("\"First Name\"", sortKey.toSql());
	}

	@Test
	public void testCreateSortKeyFunction() throws ParseException {
		// function should not be wrapped in quotes.
		SortKey sortKey = SqlElementUtils.createSortKey("max(foo)");
		assertNotNull(sortKey);
		assertEquals("MAX(foo)", sortKey.toSql());
	}

	///////////////////////////////////////////////
	// createDoubleQuotedDerivedColumn() tests
	///////////////////////////////////////////////

	@Test
	public void testCreateNonQuotedDerivedColumn() {
		DerivedColumn dr = SqlElementUtils.createNonQuotedDerivedColumn("ROW_ID");
		assertEquals("ROW_ID", dr.toSql());
	}

	@Test
	public void testCreateNonQuotedDerivedColumnWithControlFunction() {
		DerivedColumn dr = SqlElementUtils.createNonQuotedDerivedColumn("IFNULL(ROW_ID,-1) AS ROW_ID");
		assertEquals("IFNULL(ROW_ID,-1) AS ROW_ID", dr.toSql());
	}

	////////////////////////////////////////////////////////////
	// appendCombinedWhereClauseToStringBuilder() tests
	////////////////////////////////////////////////////////////
	@Test
	public void appendCombinedWhereClauseToStringBuilderNullBuilder() {
		assertThrows(IllegalArgumentException.class, () -> SqlElementUtils
				.appendCombinedWhereClauseToStringBuilder(null, searchConditionString, whereClause));
	}

	@Test
	public void appendCombinedWhereClauseToStringBuilderNullFacetSearchConditionStringNullWhereClause() {
		SqlElementUtils.appendCombinedWhereClauseToStringBuilder(stringBuilder, null, null);
		assertEquals(0, stringBuilder.length());
	}

	@Test
	public void appendCombinedWhereClauseToStringBuilderNullWhereClause() {
		SqlElementUtils.appendCombinedWhereClauseToStringBuilder(stringBuilder, searchConditionString, null);
		assertEquals(" WHERE " + searchConditionString, stringBuilder.toString());
	}

	@Test
	public void appendCombinedWhereClauseToStringBuilderNullFacetSearchConditionString() {
		SqlElementUtils.appendCombinedWhereClauseToStringBuilder(stringBuilder, null, whereClause);
		assertEquals(" WHERE " + whereClause.getSearchCondition().toSql(), stringBuilder.toString());
	}

	@Test
	public void appendCombinedWhereClauseToStringBuilderNoNulls() {
		SqlElementUtils.appendCombinedWhereClauseToStringBuilder(stringBuilder, searchConditionString, whereClause);
		assertEquals(" WHERE (" + whereClause.getSearchCondition().toSql() + ") AND (" + searchConditionString + ")",
				stringBuilder.toString());
	}

	@Test
	public void testRecursiveSetParent() throws ParseException {
		ExactNumericLiteral c0 = new ExactNumericLiteral(new Long(123));
		UnsignedNumericLiteral c1 = new UnsignedNumericLiteral(c0);
		UnsignedLiteral c2 = new UnsignedLiteral(c1);
		UnsignedValueSpecification root = new UnsignedValueSpecification(c2);
		// call under test
		root.recursiveSetParent();
		assertEquals(c1, c0.getParent());
		assertEquals(c2, c1.getParent());
		assertEquals(root, c2.getParent());
		assertNull(root.getParent());
	}

	@Test
	public void testRecursiveClearParent() throws ParseException {
		ExactNumericLiteral c0 = new ExactNumericLiteral(new Long(123));
		UnsignedNumericLiteral c1 = new UnsignedNumericLiteral(c0);
		UnsignedLiteral c2 = new UnsignedLiteral(c1);
		UnsignedValueSpecification root = new UnsignedValueSpecification(c2);
		root.recursiveSetParent();
		assertEquals(c1, c0.getParent());
		assertEquals(c2, c1.getParent());
		assertEquals(root, c2.getParent());
		assertNull(root.getParent());

		// call under test
		c2.recursiveClearParent();

		assertNull(c2.getParent());
		assertNull(c1.getParent());
		assertNull(c0.getParent());
	}

}