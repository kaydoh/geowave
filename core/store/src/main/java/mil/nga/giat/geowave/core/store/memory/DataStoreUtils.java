package mil.nga.giat.geowave.core.store.memory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.TreeSet;

import mil.nga.giat.geowave.core.index.ByteArrayId;
import mil.nga.giat.geowave.core.index.ByteArrayRange;
import mil.nga.giat.geowave.core.index.ByteArrayRange.MergeOperation;
import mil.nga.giat.geowave.core.index.IndexMetaData;
import mil.nga.giat.geowave.core.index.NumericIndexStrategy;
import mil.nga.giat.geowave.core.index.StringUtils;
import mil.nga.giat.geowave.core.index.sfc.data.MultiDimensionalNumericData;
import mil.nga.giat.geowave.core.store.DataStoreEntryInfo;
import mil.nga.giat.geowave.core.store.DataStoreEntryInfo.FieldInfo;
import mil.nga.giat.geowave.core.store.IngestCallback;
import mil.nga.giat.geowave.core.store.adapter.AdapterPersistenceEncoding;
import mil.nga.giat.geowave.core.store.adapter.DataAdapter;
import mil.nga.giat.geowave.core.store.adapter.IndexedAdapterPersistenceEncoding;
import mil.nga.giat.geowave.core.store.adapter.WritableDataAdapter;
import mil.nga.giat.geowave.core.store.adapter.statistics.DataStatistics;
import mil.nga.giat.geowave.core.store.adapter.statistics.RowRangeHistogramStatistics;
import mil.nga.giat.geowave.core.store.data.DataWriter;
import mil.nga.giat.geowave.core.store.data.PersistentDataset;
import mil.nga.giat.geowave.core.store.data.PersistentValue;
import mil.nga.giat.geowave.core.store.data.VisibilityWriter;
import mil.nga.giat.geowave.core.store.data.field.FieldReader;
import mil.nga.giat.geowave.core.store.data.field.FieldVisibilityHandler;
import mil.nga.giat.geowave.core.store.data.field.FieldWriter;
import mil.nga.giat.geowave.core.store.data.visibility.UnconstrainedVisibilityHandler;
import mil.nga.giat.geowave.core.store.data.visibility.UniformVisibilityWriter;
import mil.nga.giat.geowave.core.store.index.CommonIndexModel;
import mil.nga.giat.geowave.core.store.index.CommonIndexValue;
import mil.nga.giat.geowave.core.store.index.PrimaryIndex;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;

/*
 */
public class DataStoreUtils
{
	private final static Logger LOGGER = Logger.getLogger(DataStoreUtils.class);

	@SuppressWarnings({
		"rawtypes",
		"unchecked"
	})
	public static final UniformVisibilityWriter UNCONSTRAINED_VISIBILITY = new UniformVisibilityWriter(
			new UnconstrainedVisibilityHandler());

	public static <T> long cardinality(
			final PrimaryIndex index,
			final Map<ByteArrayId, DataStatistics<T>> stats,
			List<ByteArrayRange> ranges ) {
		RowRangeHistogramStatistics rangeStats = (RowRangeHistogramStatistics) stats.get(RowRangeHistogramStatistics
				.composeId(index.getId()));
		if (rangeStats == null) return Long.MAX_VALUE - 1;
		long count = 0;
		for (ByteArrayRange range : ranges) {
			count += rangeStats.cardinality(
					range.getStart().getBytes(),
					range.getEnd().getBytes());
		}
		return count;
	}

	public static <T> void readFieldInfo(
			List<FieldInfo<?>> fieldInfoList,
			PersistentDataset<CommonIndexValue> indexData,
			PersistentDataset<Object> extendedData,
			PersistentDataset<byte[]> unknownData,
			byte[] compositeFieldIdBytes,
			byte[] commonVisiblity,
			byte[] byteValue,
			DataAdapter<T> adapter,
			CommonIndexModel indexModel ) {
		final ByteArrayId compositeFieldId = new ByteArrayId(
				compositeFieldIdBytes);
		List<FlattenedFieldInfo> fieldInfos = DataStoreUtils.decomposeFlattenedFields(
				compositeFieldId.getBytes(),
				byteValue,
				commonVisiblity,
				-1).getFieldsRead();
		for (final FlattenedFieldInfo fieldInfo : fieldInfos) {
			final ByteArrayId fieldId = adapter.getFieldIdForPosition(
					indexModel,
					fieldInfo.getFieldPosition());
			final FieldReader<? extends CommonIndexValue> indexFieldReader = indexModel.getReader(fieldId);
			if (indexFieldReader != null) {
				final CommonIndexValue indexValue = indexFieldReader.readField(fieldInfo.getValue());
				indexValue.setVisibility(commonVisiblity);
				final PersistentValue<CommonIndexValue> val = new PersistentValue<CommonIndexValue>(
						fieldId,
						indexValue);
				indexData.addValue(val);
				fieldInfoList.add(DataStoreUtils.getFieldInfo(
						val,
						fieldInfo.getValue(),
						commonVisiblity));
			}
			else {
				final FieldReader<?> extFieldReader = adapter.getReader(fieldId);
				if (extFieldReader != null) {
					final Object value = extFieldReader.readField(fieldInfo.getValue());
					final PersistentValue<Object> val = new PersistentValue<Object>(
							fieldId,
							value);
					extendedData.addValue(val);
					fieldInfoList.add(DataStoreUtils.getFieldInfo(
							val,
							fieldInfo.getValue(),
							commonVisiblity));
				}
				else {
					LOGGER.error("field reader not found for data entry, the value may be ignored");
					unknownData.addValue(new PersistentValue<byte[]>(
							fieldId,
							fieldInfo.getValue()));
				}
			}
		}
	}

	/**
	 *
	 * Takes a byte array representing a serialized composite group of
	 * FieldInfos sharing a common visibility and returns a List of the
	 * individual FieldInfos
	 *
	 * @param compositeFieldId
	 *            the composite bitmask representing the fields contained within
	 *            the flattenedValue
	 * @param flattenedValue
	 *            the serialized composite FieldInfo
	 * @param commonVisibility
	 *            the shared visibility
	 * @param maxFieldPosition
	 *            can short-circuit read and defer decomposition of fields after
	 *            a given position
	 * @return the dataset that has been read
	 */
	public static <T> FlattenedDataSet decomposeFlattenedFields(
			final byte[] bitmask,
			final byte[] flattenedValue,
			final byte[] commonVisibility,
			final int maxFieldPosition ) {
		final List<FlattenedFieldInfo> fieldInfoList = new ArrayList<FlattenedFieldInfo>();
		final List<Integer> fieldPositions = BitmaskUtils.getFieldPositions(bitmask);

		final boolean sharedVisibility = fieldPositions.size() > 1;
		if (sharedVisibility) {
			final ByteBuffer input = ByteBuffer.wrap(flattenedValue);
			for (int i = 0; i < fieldPositions.size(); i++) {
				final Integer fieldPosition = fieldPositions.get(i);
				if ((maxFieldPosition > -1) && (fieldPosition > maxFieldPosition)) {
					return new FlattenedDataSet(
							fieldInfoList,
							new FlattenedUnreadDataSingleRow(
									input,
									i,
									fieldPositions));
				}
				final int fieldLength = input.getInt();
				final byte[] fieldValueBytes = new byte[fieldLength];
				input.get(fieldValueBytes);
				fieldInfoList.add(new FlattenedFieldInfo(
						fieldPosition,
						fieldValueBytes));
			}
		}
		else {
			fieldInfoList.add(new FlattenedFieldInfo(
					fieldPositions.get(0),
					flattenedValue));

		}
		return new FlattenedDataSet(
				fieldInfoList,
				null);
	}

	public static List<ByteArrayRange> constraintsToByteArrayRanges(
			final List<MultiDimensionalNumericData> constraints,
			final NumericIndexStrategy indexStrategy,
			final int maxRanges,
			final IndexMetaData... hints ) {
		if ((constraints == null) || constraints.isEmpty()) {
			return new ArrayList<ByteArrayRange>(); // implies in negative and
			// positive infinity
		}
		else {
			final List<ByteArrayRange> ranges = new ArrayList<ByteArrayRange>();
			for (final MultiDimensionalNumericData nd : constraints) {
				ranges.addAll(indexStrategy.getQueryRanges(
						nd,
						maxRanges,
						hints));
			}
			if (constraints.size() > 1) {
				return ByteArrayRange.mergeIntersections(
						ranges,
						MergeOperation.UNION);
			}
			return ranges;
		}
	}

	public static boolean isAuthorized(
			final byte[] visibility,
			final String[] authorizations ) {
		if ((visibility == null) || (visibility.length == 0)) {
			return true;
		}
		VisibilityExpression expr;
		try {
			expr = new VisibilityExpressionParser().parse(visibility);
		}
		catch (final IOException e) {
			LOGGER.error(
					"invalid visibility " + Arrays.toString(visibility),
					e);
			return false;
		}
		return expr.ok(authorizations);
	}

	public static String getQualifiedTableName(
			final String tableNamespace,
			final String unqualifiedTableName ) {
		return ((tableNamespace == null) || tableNamespace.isEmpty()) ? unqualifiedTableName : tableNamespace + "_"
				+ unqualifiedTableName;
	}

	public static <T> List<EntryRow> entryToRows(
			final WritableDataAdapter<T> dataWriter,
			final PrimaryIndex index,
			final T entry,
			final IngestCallback<T> ingestCallback,
			final VisibilityWriter<T> customFieldVisibilityWriter ) {
		final DataStoreEntryInfo ingestInfo = getIngestInfo(
				dataWriter,
				index,
				entry,
				customFieldVisibilityWriter);
		ingestCallback.entryIngested(
				ingestInfo,
				entry);
		return buildRows(
				dataWriter.getAdapterId().getBytes(),
				entry,
				ingestInfo);
	}

	public static List<IndexedAdapterPersistenceEncoding> getEncodings(
			final PrimaryIndex index,
			final AdapterPersistenceEncoding encoding ) {
		final List<ByteArrayId> ids = encoding.getInsertionIds(index);
		final ArrayList<IndexedAdapterPersistenceEncoding> encodings = new ArrayList<IndexedAdapterPersistenceEncoding>();
		for (final ByteArrayId id : ids) {
			encodings.add(new IndexedAdapterPersistenceEncoding(
					encoding.getAdapterId(),
					encoding.getDataId(),
					id,
					ids.size(),
					encoding.getCommonData(),
					encoding.getUnknownData(),
					encoding.getAdapterExtendedData()));
		}
		return encodings;
	}

	protected static IndexedAdapterPersistenceEncoding getEncoding(
			final CommonIndexModel model,
			final DataAdapter<?> adapter,
			final EntryRow row ) {
		final PersistentDataset<CommonIndexValue> commonData = new PersistentDataset<CommonIndexValue>();
		final PersistentDataset<byte[]> unknownData = new PersistentDataset<byte[]>();
		final PersistentDataset<Object> extendedData = new PersistentDataset<Object>();
		for (final FieldInfo column : row.info.getFieldInfo()) {
			final FieldReader<? extends CommonIndexValue> reader = model.getReader(column.getDataValue().getId());
			if (reader == null) {
				final FieldReader extendedReader = adapter.getReader(column.getDataValue().getId());
				if (extendedReader != null) {
					extendedData.addValue(column.getDataValue());
				}
				else {
					unknownData.addValue(new PersistentValue<byte[]>(
							column.getDataValue().getId(),
							column.getWrittenValue()));
				}
			}
			else {
				commonData.addValue(column.getDataValue());
			}
		}
		return new IndexedAdapterPersistenceEncoding(
				new ByteArrayId(
						row.getTableRowId().getAdapterId()),
				new ByteArrayId(
						row.getTableRowId().getDataId()),
				new ByteArrayId(
						row.getTableRowId().getInsertionId()),
				row.getTableRowId().getNumberOfDuplicates(),
				commonData,
				unknownData,
				extendedData);
	}

	private static <T> List<EntryRow> buildRows(
			final byte[] adapterId,
			final T entry,
			final DataStoreEntryInfo ingestInfo ) {
		final List<EntryRow> rows = new ArrayList<EntryRow>();
		for (final ByteArrayId rowId : ingestInfo.getRowIds()) {
			rows.add(new EntryRow(
					rowId,
					entry,
					ingestInfo));
		}
		return rows;
	}

	/**
	 * 
	 * @param dataWriter
	 * @param index
	 * @param entry
	 * @return List of zero or more matches
	 */
	public static <T> List<ByteArrayId> getRowIds(
			final WritableDataAdapter<T> dataWriter,
			final PrimaryIndex index,
			final T entry ) {
		final CommonIndexModel indexModel = index.getIndexModel();
		final AdapterPersistenceEncoding encodedData = dataWriter.encode(
				entry,
				indexModel);
		final List<ByteArrayId> insertionIds = encodedData.getInsertionIds(index);
		final List<ByteArrayId> rowIds = new ArrayList<ByteArrayId>(
				insertionIds.size());

		addToRowIds(
				rowIds,
				insertionIds,
				dataWriter.getDataId(
						entry).getBytes(),
				dataWriter.getAdapterId().getBytes(),
				encodedData.isDeduplicationEnabled());

		return rowIds;
	}

	public static <T> void addToRowIds(
			final List<ByteArrayId> rowIds,
			final List<ByteArrayId> insertionIds,
			final byte[] dataId,
			final byte[] adapterId,
			final boolean enableDeduplication ) {

		final int numberOfDuplicates = insertionIds.size() - 1;

		for (final ByteArrayId insertionId : insertionIds) {
			final byte[] indexId = insertionId.getBytes();
			// because the combination of the adapter ID and data ID
			// gaurantees uniqueness, we combine them in the row ID to
			// disambiguate index values that are the same, also adding
			// enough length values to be able to read the row ID again, we
			// lastly add a number of duplicates which can be useful as
			// metadata in our de-duplication
			// step
			rowIds.add(new ByteArrayId(
					new EntryRowID(
							indexId,
							dataId,
							adapterId,
							enableDeduplication ? numberOfDuplicates : -1).getRowId()));
		}
	}

	@SuppressWarnings({
		"rawtypes",
		"unchecked"
	})
	public static <T> DataStoreEntryInfo getIngestInfo(
			final WritableDataAdapter<T> dataWriter,
			final PrimaryIndex index,
			final T entry,
			final VisibilityWriter<T> customFieldVisibilityWriter ) {
		final CommonIndexModel indexModel = index.getIndexModel();

		final AdapterPersistenceEncoding encodedData = dataWriter.encode(
				entry,
				indexModel);
		final List<ByteArrayId> insertionIds = encodedData.getInsertionIds(index);
		final List<ByteArrayId> rowIds = new ArrayList<ByteArrayId>(
				insertionIds.size());
		final PersistentDataset extendedData = encodedData.getAdapterExtendedData();
		final PersistentDataset indexedData = encodedData.getCommonData();
		final List<PersistentValue> extendedValues = extendedData.getValues();
		final List<PersistentValue> commonValues = indexedData.getValues();

		final List<FieldInfo<?>> fieldInfoList = new ArrayList<FieldInfo<?>>();

		final byte[] dataId = dataWriter.getDataId(
				entry).getBytes();
		if (!insertionIds.isEmpty()) {
			addToRowIds(
					rowIds,
					insertionIds,
					dataId,
					dataWriter.getAdapterId().getBytes(),
					encodedData.isDeduplicationEnabled());

			for (final PersistentValue fieldValue : commonValues) {
				final FieldInfo<T> fieldInfo = getFieldInfo(
						indexModel,
						fieldValue,
						entry,
						customFieldVisibilityWriter);
				if (fieldInfo != null) {
					fieldInfoList.add(fieldInfo);
				}
			}
			for (final PersistentValue fieldValue : extendedValues) {
				if (fieldValue.getValue() != null) {
					final FieldInfo<T> fieldInfo = getFieldInfo(
							dataWriter,
							fieldValue,
							entry,
							customFieldVisibilityWriter);
					if (fieldInfo != null) {
						fieldInfoList.add(fieldInfo);
					}
				}
			}
			return new DataStoreEntryInfo(
					dataId,
					insertionIds,
					rowIds,
					fieldInfoList);
		}
		LOGGER.warn("Indexing failed to produce insertion ids; entry [" + dataWriter.getDataId(
				entry).getString() + "] not saved.");
		return new DataStoreEntryInfo(
				dataId,
				Collections.EMPTY_LIST,
				Collections.EMPTY_LIST,
				Collections.EMPTY_LIST);

	}

	@SuppressWarnings({
		"rawtypes",
		"unchecked"
	})
	public static <T> FieldInfo<T> getFieldInfo(
			final DataWriter dataWriter,
			final PersistentValue<T> fieldValue,
			final T entry,
			final VisibilityWriter<T> customFieldVisibilityWriter ) {
		final FieldWriter fieldWriter = dataWriter.getWriter(fieldValue.getId());
		final FieldVisibilityHandler<T, Object> customVisibilityHandler = customFieldVisibilityWriter
				.getFieldVisibilityHandler(fieldValue.getId());
		if (fieldWriter != null) {
			final Object value = fieldValue.getValue();
			return new FieldInfo<T>(
					fieldValue,
					fieldWriter.writeField(value),
					merge(
							customVisibilityHandler.getVisibility(
									entry,
									fieldValue.getId(),
									value),
							fieldWriter.getVisibility(
									entry,
									fieldValue.getId(),
									value)));
		}
		else if (fieldValue.getValue() != null) {
			LOGGER.warn("Data writer of class " + dataWriter.getClass() + " does not support field for "
					+ fieldValue.getValue());
		}
		return null;
	}

	@SuppressWarnings({
		"rawtypes",
		"unchecked"
	})
	public static <T> FieldInfo<T> getFieldInfo(
			final PersistentValue<T> fieldValue,
			final byte[] value,
			final byte[] visibility ) {
		return new FieldInfo<T>(
				fieldValue,
				value,
				visibility);
	}

	private static final byte[] BEG_AND_BYTE = "&".getBytes(StringUtils.GEOWAVE_CHAR_SET);
	private static final byte[] END_AND_BYTE = ")".getBytes(StringUtils.GEOWAVE_CHAR_SET);

	private static byte[] merge(
			final byte vis1[],
			final byte vis2[] ) {
		if ((vis1 == null) || (vis1.length == 0)) {
			return vis2;
		}
		else if ((vis2 == null) || (vis2.length == 0)) {
			return vis1;
		}

		final ByteBuffer buffer = ByteBuffer.allocate(vis1.length + 3 + vis2.length);
		buffer.putChar('(');
		buffer.put(vis1);
		buffer.putChar(')');
		buffer.put(BEG_AND_BYTE);
		buffer.put(vis2);
		buffer.put(END_AND_BYTE);
		return buffer.array();
	}

	private abstract static class VisibilityExpression
	{

		public abstract boolean ok(
				String[] auths );

		public VisibilityExpression and() {
			final AndExpression exp = new AndExpression();
			exp.add(this);
			return exp;
		}

		public VisibilityExpression or() {
			final OrExpression exp = new OrExpression();
			exp.add(this);
			return exp;
		}

		public abstract List<VisibilityExpression> children();

		public abstract VisibilityExpression add(
				VisibilityExpression expression );

	}

	public static enum NodeType {
		TERM,
		OR,
		AND,
	}

	private static class VisibilityExpressionParser
	{
		private int index = 0;
		private int parens = 0;

		public VisibilityExpressionParser() {}

		VisibilityExpression parse(
				final byte[] expression )
				throws IOException {
			if (expression.length > 0) {
				final VisibilityExpression expr = parse_(expression);
				if (expr == null) {
					badArgumentException(
							"operator or missing parens",
							expression,
							index - 1);
				}
				if (parens != 0) {
					badArgumentException(
							"parenthesis mis-match",
							expression,
							index - 1);
				}
				return expr;
			}
			return null;
		}

		VisibilityExpression processTerm(
				final int start,
				final int end,
				final VisibilityExpression expr,
				final byte[] expression )
				throws UnsupportedEncodingException {
			if (start != end) {
				if (expr != null) {
					badArgumentException(
							"expression needs | or &",
							expression,
							start);
				}
				return new ChildExpression(
						new String(
								Arrays.copyOfRange(
										expression,
										start,
										end),
								"UTF-8"));
			}
			if (expr == null) {
				badArgumentException(
						"empty term",
						Arrays.copyOfRange(
								expression,
								start,
								end),
						start);
			}
			return expr;
		}

		VisibilityExpression parse_(
				final byte[] expression )
				throws IOException {
			VisibilityExpression result = null;
			VisibilityExpression expr = null;
			int termStart = index;
			while (index < expression.length) {
				switch (expression[index++]) {
					case '&': {
						expr = processTerm(
								termStart,
								index - 1,
								expr,
								expression);
						if (result != null) {
							if (!(result instanceof AndExpression)) {
								badArgumentException(
										"cannot mix & and |",
										expression,
										index - 1);
							}
						}
						else {
							result = new AndExpression();
						}
						result.add(expr);
						expr = null;
						termStart = index;
						break;
					}
					case '|': {
						expr = processTerm(
								termStart,
								index - 1,
								expr,
								expression);
						if (result != null) {
							if (!(result instanceof OrExpression)) {
								badArgumentException(
										"cannot mix | and &",
										expression,
										index - 1);
							}
						}
						else {
							result = new OrExpression();
						}
						result.add(expr);
						expr = null;
						termStart = index;
						break;
					}
					case '(': {
						parens++;
						if ((termStart != (index - 1)) || (expr != null)) {
							badArgumentException(
									"expression needs & or |",
									expression,
									index - 1);
						}
						expr = parse_(expression);
						termStart = index;
						break;
					}
					case ')': {
						parens--;
						final VisibilityExpression child = processTerm(
								termStart,
								index - 1,
								expr,
								expression);
						if ((child == null) && (result == null)) {
							badArgumentException(
									"empty expression not allowed",
									expression,
									index);
						}
						if (result == null) {
							return child;
						}
						result.add(child);
						return result;
					}
				}
			}
			final VisibilityExpression child = processTerm(
					termStart,
					index,
					expr,
					expression);
			if (result != null) {
				result.add(child);
			}
			else {
				result = child;
			}
			if (!(result instanceof ChildExpression)) {
				if (result.children().size() < 2) {
					badArgumentException(
							"missing term",
							expression,
							index);
				}
			}
			return result;
		}
	}

	public abstract static class CompositeExpression extends
			VisibilityExpression
	{
		protected final List<VisibilityExpression> expressions = new ArrayList<VisibilityExpression>();

		@Override
		public VisibilityExpression add(
				final VisibilityExpression expression ) {
			if (expression.getClass().equals(
					this.getClass())) {
				for (final VisibilityExpression child : expression.children()) {
					add(child);
				}
			}
			else {
				expressions.add(expression);
			}
			return this;
		}
	}

	public static class ChildExpression extends
			VisibilityExpression
	{
		private final String value;

		public ChildExpression(
				final String value ) {
			super();
			this.value = value;
		}

		@Override
		public boolean ok(
				final String[] auths ) {
			if (auths != null) {
				for (final String auth : auths) {
					if (value.equals(auth)) {
						return true;
					}
				}
			}
			return false;
		}

		@Override
		public List<VisibilityExpression> children() {
			return Collections.emptyList();
		}

		@Override
		public VisibilityExpression add(
				final VisibilityExpression expression ) {
			return this;
		}
	}

	public static class AndExpression extends
			CompositeExpression
	{

		@Override
		public List<VisibilityExpression> children() {
			return expressions;
		}

		@Override
		public boolean ok(
				final String[] auth ) {
			for (final VisibilityExpression expression : expressions) {
				if (!expression.ok(auth)) {
					return false;
				}
			}
			return true;
		}

		public VisibilityExpression and(
				final VisibilityExpression expression ) {
			return this;
		}
	}

	public static class OrExpression extends
			CompositeExpression
	{

		@Override
		public boolean ok(
				final String[] auths ) {
			for (final VisibilityExpression expression : expressions) {
				if (expression.ok(auths)) {
					return true;
				}
			}
			return false;
		}

		@Override
		public List<VisibilityExpression> children() {
			return expressions;
		}

		public VisibilityExpression or(
				final VisibilityExpression expression ) {
			return this;
		}

	}

	private static final void badArgumentException(
			final String msg,
			final byte[] expression,
			final int place ) {
		throw new IllegalArgumentException(
				msg + " for " + Arrays.toString(expression) + " at " + place);
	}

	/**
	 * This method combines all FieldInfos that share a common visibility into a
	 * single FieldInfo
	 *
	 * @param originalList
	 * @return a new list of composite FieldInfos
	 */
	public static <T> List<FieldInfo<?>> composeFlattenedFields(
			final List<FieldInfo<?>> originalList,
			final CommonIndexModel model,
			final WritableDataAdapter<?> writableAdapter ) {
		final List<FieldInfo<?>> retVal = new ArrayList<>();
		final Map<ByteArrayId, List<Pair<Integer, FieldInfo<?>>>> vizToFieldMap = new LinkedHashMap<>();
		boolean sharedVisibility = false;
		// organize FieldInfos by unique visibility
		for (final FieldInfo<?> fieldInfo : originalList) {
			final int fieldPosition = writableAdapter.getPositionOfOrderedField(
					model,
					fieldInfo.getDataValue().getId());
			if (fieldPosition == -1) {
				writableAdapter.getPositionOfOrderedField(
						model,
						fieldInfo.getDataValue().getId());
			}
			final ByteArrayId currViz = new ByteArrayId(
					fieldInfo.getVisibility());
			if (vizToFieldMap.containsKey(currViz)) {
				sharedVisibility = true;
				final List<Pair<Integer, FieldInfo<?>>> listForViz = vizToFieldMap.get(currViz);
				listForViz.add(new ImmutablePair<Integer, DataStoreEntryInfo.FieldInfo<?>>(
						fieldPosition,
						fieldInfo));
			}
			else {
				final List<Pair<Integer, FieldInfo<?>>> listForViz = new ArrayList<>();
				listForViz.add(new ImmutablePair<Integer, DataStoreEntryInfo.FieldInfo<?>>(
						fieldPosition,
						fieldInfo));
				vizToFieldMap.put(
						currViz,
						listForViz);
			}
		}
		if (!sharedVisibility) {
			// at a minimum, must return transformed (bitmasked) fieldInfos
			final List<FieldInfo<?>> bitmaskedFieldInfos = new ArrayList<>();
			for (final List<Pair<Integer, FieldInfo<?>>> list : vizToFieldMap.values()) {
				// every list must have exactly one element
				final Pair<Integer, FieldInfo<?>> fieldInfo = list.get(0);
				bitmaskedFieldInfos.add(new FieldInfo<>(
						new PersistentValue<Object>(
								new ByteArrayId(
										BitmaskUtils.generateCompositeBitmask(fieldInfo.getLeft())),
								fieldInfo.getRight().getDataValue().getValue()),
						fieldInfo.getRight().getWrittenValue(),
						fieldInfo.getRight().getVisibility()));
			}
			return bitmaskedFieldInfos;
		}
		for (final Entry<ByteArrayId, List<Pair<Integer, FieldInfo<?>>>> entry : vizToFieldMap.entrySet()) {
			final List<byte[]> fieldInfoBytesList = new ArrayList<>();
			int totalLength = 0;
			final SortedSet<Integer> fieldPositions = new TreeSet<Integer>();
			final List<Pair<Integer, FieldInfo<?>>> fieldInfoList = entry.getValue();
			Collections.sort(
					fieldInfoList,
					new BitmaskedFieldInfoComparator());
			for (final Pair<Integer, FieldInfo<?>> fieldInfoPair : fieldInfoList) {
				final FieldInfo<?> fieldInfo = fieldInfoPair.getRight();
				final ByteBuffer fieldInfoBytes = ByteBuffer.allocate(4 + fieldInfo.getWrittenValue().length);
				fieldPositions.add(fieldInfoPair.getLeft());
				fieldInfoBytes.putInt(fieldInfo.getWrittenValue().length);
				fieldInfoBytes.put(fieldInfo.getWrittenValue());
				fieldInfoBytesList.add(fieldInfoBytes.array());
				totalLength += fieldInfoBytes.array().length;
			}
			final ByteBuffer allFields = ByteBuffer.allocate(totalLength);
			for (final byte[] bytes : fieldInfoBytesList) {
				allFields.put(bytes);
			}
			final byte[] compositeBitmask = BitmaskUtils.generateCompositeBitmask(fieldPositions);
			final FieldInfo<?> composite = new FieldInfo<T>(
					new PersistentValue<T>(
							new ByteArrayId(
									compositeBitmask),
							null), // unnecessary
					allFields.array(),
					entry.getKey().getBytes());
			retVal.add(composite);
		}
		return retVal;
	}
}
