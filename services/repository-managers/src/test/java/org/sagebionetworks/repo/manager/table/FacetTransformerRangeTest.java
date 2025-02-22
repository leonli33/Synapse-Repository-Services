package org.sagebionetworks.repo.manager.table;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.sagebionetworks.repo.model.dbo.dao.table.TableModelTestUtils;
import org.sagebionetworks.repo.model.entity.IdAndVersion;
import org.sagebionetworks.repo.model.table.ColumnModel;
import org.sagebionetworks.repo.model.table.FacetColumnRangeRequest;
import org.sagebionetworks.repo.model.table.FacetColumnResultRange;
import org.sagebionetworks.repo.model.table.FacetType;
import org.sagebionetworks.repo.model.table.Row;
import org.sagebionetworks.repo.model.table.RowSet;
import org.sagebionetworks.repo.model.table.SelectColumn;
import org.sagebionetworks.table.cluster.SchemaProvider;
import org.sagebionetworks.table.cluster.TranslationDependencies;
import org.sagebionetworks.table.cluster.description.TableIndexDescription;
import org.sagebionetworks.table.query.ParseException;
import org.sagebionetworks.table.query.TableQueryParser;
import org.sagebionetworks.table.query.model.TableExpression;
import org.sagebionetworks.table.query.util.FacetRequestColumnModel;
import org.springframework.test.util.ReflectionTestUtils;

import com.google.common.collect.Lists;

@RunWith(MockitoJUnitRunner.class)
public class FacetTransformerRangeTest {
	private FacetTransformerRange facetTransformer;
	private String columnName;
	private String selectedMin;
	private String selectedMax;
	private List<ColumnModel> schema;
	private String originalSearchCondition;
	private List<FacetRequestColumnModel> facets;
	private RowSet rowSet;
	private List<SelectColumn> correctSelectList;
	private Long userId;
	private TableExpression originalQuery;
	private TranslationDependencies dependencies;
	
	@Before
	public void before() throws ParseException{
		schema = TableModelTestUtils.createOneOfEachType(true);
		assertFalse(schema.isEmpty());
		columnName = "i2";
		facets = new ArrayList<>();
		FacetColumnRangeRequest rangeRequest = new FacetColumnRangeRequest();
		rangeRequest.setColumnName(columnName);
		rangeRequest.setMin(selectedMin);
		rangeRequest.setMax(selectedMax);
		facets.add(new FacetRequestColumnModel(schema.get(2), rangeRequest)); //use column "i2"
		selectedMin = "12";
		selectedMax = "34";
		originalSearchCondition = "i0 LIKE 'asdf%'";
		userId = 1L;
		SchemaProvider schemaProvider = Mockito.mock(SchemaProvider.class);
		when(schemaProvider.getTableSchema(any())).thenReturn(schema);
		
		dependencies = TranslationDependencies.builder().setSchemaProvider(schemaProvider)
				.setIndexDescription(new TableIndexDescription(IdAndVersion.parse("syn123"))).setUserId(userId).build();
		
		originalQuery = new TableQueryParser("FROM syn123 WHERE " + originalSearchCondition).tableExpression();
		
		rowSet = new RowSet();
		
		SelectColumn col1 = new SelectColumn();
		col1.setName(FacetTransformerRange.MIN_ALIAS);
		SelectColumn col2 = new SelectColumn();
		col2.setName(FacetTransformerRange.MAX_ALIAS);
		correctSelectList = Lists.newArrayList(col1, col2);
		
		facetTransformer = new FacetTransformerRange(columnName, facets, originalQuery, dependencies, selectedMin, selectedMax);		

	}
	/////////////////////////////////
	// constructor tests()
	/////////////////////////////////
	@Test
	public void testConstructor() {
		assertEquals(columnName, ReflectionTestUtils.getField(facetTransformer, "columnName"));
		assertEquals(facets, ReflectionTestUtils.getField(facetTransformer, "facets"));
		assertEquals(selectedMin, ReflectionTestUtils.getField(facetTransformer, "selectedMin"));
		assertEquals(selectedMax, ReflectionTestUtils.getField(facetTransformer, "selectedMax"));		
		//this is tested in a separate test
		assertNotNull(ReflectionTestUtils.getField(facetTransformer, "generatedFacetSqlQuery"));
	}
	
	/////////////////////////////////
	// generateFacetSqlQuery tests()
	/////////////////////////////////
	
	@Test
	public void testGenerateFacetSqlQuery(){
		//check the non-transformed sql
		String expectedString = "SELECT MIN(_C2_) AS minimum, MAX(_C2_) AS maximum FROM T123 WHERE _C0_ LIKE :b0";
		assertEquals(expectedString, facetTransformer.getFacetSqlQuery().getOutputSQL());
		assertEquals("asdf%", facetTransformer.getFacetSqlQuery().getParameters().get("b0"));
	}
	
	////////////////////////////
	// translateToResult() tests
	////////////////////////////
	@Test (expected = IllegalArgumentException.class)
	public void testTranslateToResultNullRowSet(){
		facetTransformer.translateToResult(null);
	}
	
	@Test (expected = IllegalArgumentException.class)
	public void testTranslateToResultWrongHeaders(){
		rowSet.setHeaders(new ArrayList<SelectColumn>());
		facetTransformer.translateToResult(rowSet);
	}
	
	@Test (expected = IllegalArgumentException.class)
	public void testTranslateToResultCorrectHeadersWrongNumRows(){
		rowSet.setHeaders(correctSelectList);
		rowSet.setRows(new ArrayList<Row>());
		facetTransformer.translateToResult(rowSet);
	}
	
	@Test 
	public void testTranslateToResultCorrectHeadersAndNumRows(){
		String colMin = "2";
		String colMax = "42";
		rowSet.setHeaders(correctSelectList);
		Row row = new Row();
		row.setValues(Lists.newArrayList(colMin, colMax));
		rowSet.setRows(Lists.newArrayList(row));
		FacetColumnResultRange result = (FacetColumnResultRange) facetTransformer.translateToResult(rowSet);

		assertEquals(columnName, result.getColumnName());
		assertEquals(FacetType.range, result.getFacetType());

		assertEquals(colMin, result.getColumnMin());
		assertEquals(colMax, result.getColumnMax());
		assertEquals(selectedMin, result.getSelectedMin());
		assertEquals(selectedMax, result.getSelectedMax());
	}
}
