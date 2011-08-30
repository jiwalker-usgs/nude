package gov.usgs.cida.connector.http;

import gov.usgs.cida.connector.parser.IParser;
import gov.usgs.cida.spec.jsl.SpecValue;

import java.sql.SQLException;

public abstract class AbstractHttpParser<Table extends Enum<Table>> implements IParser<Table> {
	
	@Override
	public <T> SpecValue<T> getValue(Class<T> type, int columnIndex)
			throws SQLException {
		if (String.class.equals(type)) {
			return new SpecValue<T>((T) getValue(columnIndex));
		} else {
			throw new SQLException("Operation not supported");
		}
	}
	
	public abstract String getValue(int columnIndex) throws SQLException;
}
