package org.sagebionetworks.repo.manager.table;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.repo.model.table.TableConstants.NULL_VALUE_KEYWORD;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.sagebionetworks.repo.model.entity.IdAndVersion;
import org.sagebionetworks.repo.model.table.ColumnModel;
import org.sagebionetworks.repo.model.table.ColumnType;
import org.sagebionetworks.repo.model.table.FacetColumnResultValueCount;
import org.sagebionetworks.repo.model.table.FacetColumnResultValues;
import org.sagebionetworks.repo.model.table.FacetColumnValuesRequest;
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
import com.google.common.collect.Sets;

public class FacetTransformerValueCountsTest {
	String selectedValue;
	String notSelectedValue;
	private List<ColumnModel> schema;
	private TableExpression originalQuery;
	private TranslationDependencies dependencies;
	private String originalSearchCondition;
	private List<FacetRequestColumnModel> facets;
	private RowSet rowSet;
	private List<SelectColumn> correctSelectList;
	private Set<String> selectedValuesSet;
	private ColumnModel stringModel;
	private ColumnModel stringListModel;
	private Long userId;

	
	@BeforeEach
	public void before() throws ParseException{
		stringModel = new ColumnModel();
		stringModel.setName("stringColumn");
		stringModel.setColumnType(ColumnType.STRING);
		stringModel.setId("1");
		stringModel.setFacetType(FacetType.enumeration);
		stringModel.setMaximumSize(50L);

		stringListModel = new ColumnModel();
		stringListModel.setName("stringListColumn");
		stringListModel.setColumnType(ColumnType.STRING_LIST);
		stringListModel.setId("2");
		stringListModel.setFacetType(FacetType.enumeration);
		stringListModel.setMaximumSize(50L);
		stringListModel.setMaximumListLength(24L);

		schema = Arrays.asList(stringModel, stringListModel);

		facets = new ArrayList<>();
		selectedValue = "selectedValue";
		notSelectedValue = "notSelectedValue";
		FacetColumnValuesRequest valuesRequest = new FacetColumnValuesRequest();
		valuesRequest.setColumnName(stringModel.getName());
		selectedValuesSet = Sets.newHashSet(selectedValue);
		valuesRequest.setFacetValues(selectedValuesSet);
		facets.add(new FacetRequestColumnModel(schema.get(0), valuesRequest));//use column "i0"

		userId = 1L;
		SchemaProvider schemaProvider = Mockito.mock(SchemaProvider.class);
		when(schemaProvider.getTableSchema(any())).thenReturn(schema);

		originalSearchCondition = "\"stringColumn\" LIKE 'asdf%'";
		
		dependencies = TranslationDependencies.builder().setSchemaProvider(schemaProvider)
				.setIndexDescription(new TableIndexDescription(IdAndVersion.parse("syn123"))).setUserId(userId).build();
		
		originalQuery = new TableQueryParser("FROM syn123 WHERE " + originalSearchCondition).tableExpression();
		
		rowSet = new RowSet();
		
		SelectColumn col1 = new SelectColumn();
		col1.setName(FacetTransformerValueCounts.VALUE_ALIAS);
		SelectColumn col2 = new SelectColumn();
		col2.setName(FacetTransformerValueCounts.COUNT_ALIAS);
		correctSelectList = Lists.newArrayList(col1, col2);
	}
	/////////////////////////////////
	// constructor tests()
	/////////////////////////////////
	
	@Test
	public void testConstructor() {
		FacetTransformerValueCounts facetTransformer = new FacetTransformerValueCounts(stringModel.getName(), false, facets, originalQuery, dependencies, selectedValuesSet);

		assertEquals(stringModel.getName(), ReflectionTestUtils.getField(facetTransformer, "columnName"));
		assertEquals(facets, ReflectionTestUtils.getField(facetTransformer, "facets"));
		assertEquals(selectedValuesSet, ReflectionTestUtils.getField(facetTransformer, "selectedValues"));
		
		
		//this is tested in a separate test
		assertNotNull(ReflectionTestUtils.getField(facetTransformer, "generatedFacetSqlQuery"));
	}
	
	/////////////////////////////////
	// generateFacetSqlQuery tests()
	/////////////////////////////////
	@Test
	public void testGenerateFacetSqlQuery(){
		FacetTransformerValueCounts facetTransformer = new FacetTransformerValueCounts(stringModel.getName(), false, facets, originalQuery, dependencies, selectedValuesSet);

		//check the non-transformed sql
		String expectedString = "SELECT \"stringColumn\" AS value, COUNT(*) AS frequency"
				+ " FROM syn123"
				+ " WHERE \"stringColumn\" LIKE 'asdf%'"
				+ " GROUP BY \"stringColumn\""
				+ " ORDER BY frequency DESC, value ASC"
				+ " LIMIT 100";
		assertEquals(expectedString, facetTransformer.getFacetSqlQuery().getInputSql());
		
		//transformed model will be correct if schema and non-transformed query are correct
		//because it is handled by SqlQuery Constructor
	}

	@Test
	public void testGenerateFacetSqlQuery_ForListTypes(){
		FacetTransformerValueCounts facetTransformer = new FacetTransformerValueCounts(stringListModel.getName(), true, facets, originalQuery, dependencies, selectedValuesSet);

		//check the non-transformed sql
		String expectedString = "SELECT T123_INDEX_C2_._C2__UNNEST AS value, COUNT(*) AS frequency"
				+ " FROM T123 LEFT JOIN T123_INDEX_C2_ ON T123.ROW_ID = T123_INDEX_C2_.ROW_ID_REF_C2_ W"
				+ "HERE ( _C1_ LIKE :b0 ) AND ( ( ( _C1_ = :b1 ) ) ) "
				+ "GROUP BY T123_INDEX_C2_._C2__UNNEST ORDER BY frequency DESC, value ASC LIMIT :b2";
		assertEquals(expectedString, facetTransformer.getFacetSqlQuery().getOutputSQL());
		assertEquals("asdf%", facetTransformer.getFacetSqlQuery().getParameters().get("b0"));
		assertEquals("selectedValue", facetTransformer.getFacetSqlQuery().getParameters().get("b1"));
		assertEquals(100L, facetTransformer.getFacetSqlQuery().getParameters().get("b2"));
	}



	////////////////////////////
	// translateToResult() tests
	////////////////////////////
	@Test
	public void testTranslateToResultNullRowSet(){
		FacetTransformerValueCounts facetTransformer = new FacetTransformerValueCounts(stringModel.getName(), false, facets, originalQuery, dependencies, selectedValuesSet);
		assertThrows(IllegalArgumentException.class, ()->{
			facetTransformer.translateToResult(null);
		});
	}
	
	@Test
	public void testTranslateToResultWrongHeaders(){
		FacetTransformerValueCounts facetTransformer = new FacetTransformerValueCounts(stringModel.getName(), false, facets, originalQuery, dependencies, selectedValuesSet);

		rowSet.setHeaders(Collections.emptyList());

		assertThrows(IllegalArgumentException.class, ()->{
			facetTransformer.translateToResult(rowSet);
		});
	}
	
	@Test 
	public void testTranslateToResultNullValueColumn(){
		FacetTransformerValueCounts facetTransformer = new FacetTransformerValueCounts(stringModel.getName(), false, facets, originalQuery, dependencies, selectedValuesSet);

		Long row1Count = 42L;
		rowSet.setHeaders(correctSelectList);
		Row row1 = new Row();
		//the value column is null
		row1.setValues(Lists.newArrayList(null, row1Count.toString()));
	
		rowSet.setRows(Lists.newArrayList(row1));
		FacetColumnResultValues result = (FacetColumnResultValues) facetTransformer.translateToResult(rowSet);

		assertEquals(stringModel.getName(), result.getColumnName());
		assertEquals(FacetType.enumeration, result.getFacetType());

		List<FacetColumnResultValueCount> valueCounts = result.getFacetValues();
		assertEquals(1, valueCounts.size());
		
		FacetColumnResultValueCount valueCount1 = valueCounts.get(0);
		assertEquals(NULL_VALUE_KEYWORD, valueCount1.getValue());
		assertEquals(row1Count, valueCount1.getCount());
		assertFalse(valueCount1.getIsSelected());

	}
	
	@Test 
	public void testTranslateToResultCorrectHeaders(){
		FacetTransformerValueCounts facetTransformer = new FacetTransformerValueCounts(stringModel.getName(), false, facets, originalQuery, dependencies, selectedValuesSet);

		Long row1Count = 42L;
		Long row2Count = 23L;
		rowSet.setHeaders(correctSelectList);
		Row row1 = new Row();
		row1.setValues(Lists.newArrayList(selectedValue, row1Count.toString()));
		Row row2 = new Row();
		row2.setValues(Lists.newArrayList(notSelectedValue, row2Count.toString()));
		rowSet.setRows(Lists.newArrayList(row1, row2));
		FacetColumnResultValues result = (FacetColumnResultValues) facetTransformer.translateToResult(rowSet);

		assertEquals(stringModel.getName(), result.getColumnName());
		assertEquals(FacetType.enumeration, result.getFacetType());

		List<FacetColumnResultValueCount> valueCounts = result.getFacetValues();
		assertEquals(2, valueCounts.size());
		
		FacetColumnResultValueCount valueCount1 = valueCounts.get(0);
		assertEquals(selectedValue, valueCount1.getValue());
		assertEquals(row1Count, valueCount1.getCount());
		assertTrue(valueCount1.getIsSelected());
		
		FacetColumnResultValueCount valueCount2 = valueCounts.get(1);
		assertEquals(notSelectedValue, valueCount2.getValue());
		assertEquals(row2Count, valueCount2.getCount());
		assertFalse(valueCount2.getIsSelected());
	}
	
}
