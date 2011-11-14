package gov.usgs.cida.nude.filter;

import gov.usgs.cida.nude.column.Column;
import gov.usgs.cida.nude.column.ColumnGrouping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FilterStageBuilder {
	protected Map<Column, ColumnAlias> transforms;
	protected ColumnGrouping inColumns;
	protected Column primaryKey;
	protected List<Column> outColumns;
	
	public FilterStageBuilder(ColumnGrouping input) {
		this.inColumns = input;
		this.transforms = new HashMap<Column, ColumnAlias>();
		this.outColumns = new ArrayList<Column>();
		
		for (Column col : input) {
			this.addTransform(col, new ColumnAlias(col));
		}
		this.setPrimaryKey(this.inColumns.getPrimaryKey());
	}
	
	public void setPrimaryKey(Column outColumn) {
		if (this.transforms.containsKey(outColumn)) {
			this.primaryKey = outColumn;
		}
	}
	
	public void addTransform(Column outColumn, ColumnAlias transform) {
		this.transforms.put(outColumn, transform);
		this.outColumns.add(outColumn);
	}
	
//	public void filter(Column inputColumn) {
//		this.transforms.remove(inputColumn);
//	}
	
	public FilterStage buildFilterStage() {
		ColumnGrouping outCols = new ColumnGrouping(this.primaryKey, this.outColumns);
		return new FilterStage(this.inColumns, this.transforms, outCols);
	}
}
