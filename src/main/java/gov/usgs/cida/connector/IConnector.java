package gov.usgs.cida.connector;

import gov.usgs.cida.resultset.ITableView;

import java.sql.ResultSet;

public interface IConnector extends ITableView {
	public void addInput(ResultSet in);
}
