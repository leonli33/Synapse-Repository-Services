package org.sagebionetworks.table.query;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.sagebionetworks.table.query.model.OrderByClause;
import org.sagebionetworks.table.query.model.Pagination;
import org.sagebionetworks.table.query.model.TableExpression;
import org.sagebionetworks.table.query.model.WhereClause;
import org.sagebionetworks.table.query.util.SqlElementUtils;

public class TableExpressionTest {

	@Test
	public void testTableExpressionToSQLNoWhere() throws ParseException {
		TableExpression element = SqlElementUtils.createTableExpression("from syn123");
		assertEquals("FROM syn123", element.toString());
	}

	@Test
	public void testTableExpressionToSQLWithWhere() throws ParseException {
		TableExpression element = SqlElementUtils.createTableExpression("from syn123 where bar = 123");
		assertEquals("FROM syn123 WHERE bar = 123", element.toString());
	}

	@Test
	public void testTableExpressionToSQLWithGroupBy() throws ParseException {
		TableExpression element = SqlElementUtils.createTableExpression("from syn123 group by foo");
		assertEquals("FROM syn123 GROUP BY foo", element.toString());
	}

	@Test
	public void testTableExpressionToSQLWithWhereAndGroupBy() throws ParseException {
		TableExpression element = SqlElementUtils.createTableExpression("from syn123 where a=b group by foo");
		assertEquals("FROM syn123 WHERE a = b GROUP BY foo", element.toString());
	}

	@Test
	public void testTableExpressionToSQLWithPagination() throws ParseException {
		TableExpression element = SqlElementUtils.createTableExpression("from syn123 limit 1 offset 2");
		assertEquals("FROM syn123 LIMIT 1 OFFSET 2", element.toString());
	}

	@Test
	public void testTableExpressionToSQLWithWhereAndPagination() throws ParseException {
		TableExpression element = SqlElementUtils.createTableExpression("from syn123 where foo = 3 limit 1 offset 2");
		assertEquals("FROM syn123 WHERE foo = 3 LIMIT 1 OFFSET 2", element.toString());
	}

	@Test
	public void testTableExpressionToSQLWithWhereAndGroupingAndPagination() throws ParseException {
		TableExpression element = SqlElementUtils
				.createTableExpression("from syn123 where foo = 3 group by bar limit 1 offset 2");
		assertEquals("FROM syn123 WHERE foo = 3 GROUP BY bar LIMIT 1 OFFSET 2", element.toString());
	}

	@Test
	public void testTableExpressionWithOrderBy() throws ParseException {
		TableExpression element = SqlElementUtils.createTableExpression("from syn123 order by foo asc, bar desc");
		assertEquals("FROM syn123 ORDER BY foo ASC, bar DESC", element.toString());
	}

	@Test
	public void testTableExpressionWithWhereAndOrderBy() throws ParseException {
		TableExpression element = SqlElementUtils
				.createTableExpression("from syn123 where foo = 100 order by bar desc");
		assertEquals("FROM syn123 WHERE foo = 100 ORDER BY bar DESC", element.toString());
	}

	@Test
	public void testTableExpressionWithAll() throws ParseException {
		TableExpression element = SqlElementUtils.createTableExpression(
				"from syn123 where foo = 100 group by foo order by bar desc limit 100 offset 9999");
		assertEquals("FROM syn123 WHERE foo = 100 GROUP BY foo ORDER BY bar DESC LIMIT 100 OFFSET 9999",
				element.toString());
	}

	@Test
	public void testTableExpressionIsAggregateNoGroupBy() throws ParseException {
		TableExpression element = SqlElementUtils
				.createTableExpression("from syn123 where foo = 100 order by bar desc limit 100 offset 9999");
		assertFalse(element.isElementAggregate());
	}

	@Test
	public void testTableExpressionIsAggregateGroupBy() throws ParseException {
		TableExpression element = SqlElementUtils.createTableExpression("from syn123 group by one");
		assertTrue(element.isElementAggregate());
	}

	@Test
	public void testGetChildren() throws ParseException {
		TableExpression element = new TableQueryParser(
				"from syn123 where foo = 100 group by foo order by bar desc limit 100 offset 9999").tableExpression();
		assertEquals(Arrays.asList(element.getFromClause(), element.getWhereClause(), element.getGroupByClause(),
				element.getOrderByClause(), element.getPagination()), element.getChildren());
	}
	
	@Test
	public void testReplaceWhere() throws ParseException {
		TableExpression element = new TableQueryParser(
				"from syn123 where foo = 100").tableExpression();
		WhereClause oldWhere = element.getWhereClause();
		WhereClause newWhere = new TableQueryParser("where bar < 1").whereClause();
		// call under test
		element.replaceWhere(newWhere);
		assertEquals(element, newWhere.getParent());
		assertNull(oldWhere.getParent());
	}
	
	@Test
	public void testReplaceOrderBy() throws ParseException {
		TableExpression element = new TableQueryParser(
				"from syn123 where foo = 100 order by foo desc").tableExpression();
		OrderByClause old = element.getOrderByClause();
		OrderByClause newOrderBy = new TableQueryParser("order by bar asc").orderByClause();
		// call under test
		element.replaceOrderBy(newOrderBy);
		assertEquals(element, newOrderBy.getParent());
		assertNull(old.getParent());
	}
	
	@Test
	public void testReplacePagination() throws ParseException {
		TableExpression element = new TableQueryParser(
				"from syn123 where foo = 100 limit 1 offset 2").tableExpression();
		Pagination old = element.getPagination();
		Pagination newPagination = new TableQueryParser("limit 3 offset 4").pagination();
		// call under test
		element.replacePagination(newPagination);
		assertEquals(element, newPagination.getParent());
		assertNull(old.getParent());
	}

}