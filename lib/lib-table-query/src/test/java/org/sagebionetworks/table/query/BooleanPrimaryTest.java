package org.sagebionetworks.table.query;


import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.sagebionetworks.table.query.model.BooleanPrimary;
import org.sagebionetworks.table.query.model.Predicate;
import org.sagebionetworks.table.query.model.SearchCondition;
import org.sagebionetworks.table.query.util.SqlElementUntils;

public class BooleanPrimaryTest {

	@Test
	public void testBooleanPrimaryToSQLPredicate() throws ParseException{
		Predicate predicate = SqlElementUntils.createPredicate("foo in(1,2)");
		BooleanPrimary element = new BooleanPrimary(predicate);
		assertEquals("foo IN ( 1, 2 )", element.toString());
	}
	
	@Test
	public void testBooleanPrimaryToSQLSearchCondition() throws ParseException{
		SearchCondition searchCondition = SqlElementUntils.createSearchCondition("bar = 1");
		BooleanPrimary element = new BooleanPrimary(searchCondition);
		assertEquals("( bar = 1 )", element.toString());
	}
}
