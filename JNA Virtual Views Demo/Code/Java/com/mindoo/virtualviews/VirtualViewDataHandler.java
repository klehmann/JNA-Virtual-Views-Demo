package com.mindoo.virtualviews;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ibm.commons.util.io.json.JsonJavaArray;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.mindoo.domino.jna.NotesTimeDate;
import com.mindoo.domino.jna.utils.StringUtil;
import com.mindoo.domino.jna.virtualviews.VirtualView;
import com.mindoo.domino.jna.virtualviews.VirtualView.VirtualViewNavigatorBuilder;
import com.mindoo.domino.jna.virtualviews.VirtualViewEntryData;
import com.mindoo.domino.jna.virtualviews.VirtualViewNavigator;
import com.mindoo.domino.jna.virtualviews.VirtualViewNavigator.SelectedOnly;

import lotus.domino.Database;
import lotus.domino.Session;

/**
 * REST API to read virtual view data
 */
public class VirtualViewDataHandler extends InternalBaseHandler {

	@Override
	public void serviceSecured(Session session, Database db, Session sessionAsSigner, HttpServletRequest req,
			HttpServletResponse response) throws Exception {
		
		response.setContentType("application/json; charset=UTF-8");
		response.setHeader("Cache-Control", "no-cache");
		response.setCharacterEncoding("UTF-8");

		String viewId = req.getParameter("viewid");
		if (StringUtil.isEmpty(viewId)) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			
			JsonJavaObject json = new JsonJavaObject();
			json.putBoolean("success", false);
			json.putString("message", "Missing viewid parameter");
			response.getWriter().println(json);
			return;
		}

		Optional<VirtualView> optView = VirtualViews.INSTANCE.getViewById(viewId);
		if (!optView.isPresent()) {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			
			JsonJavaObject json = new JsonJavaObject();
			json.putBoolean("success", false);
			json.putString("message", "View not found: " + viewId);
			response.getWriter().println(json);
			return;
		}
		
		VirtualView view = optView.get();
		view.update();
		
		String format = req.getParameter("format");
		if (format == null || format.length()==0 || "flat".equals(format)) {
			serviceCategorizedViewData(session, db, sessionAsSigner, req, response, view);
			return;
		}
		else if ("d3stats".equals(format)) {
			serviceD3StatisticViewData(session, db, sessionAsSigner, req, response, view);
			return;			
		}
		else {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			
			JsonJavaObject json = new JsonJavaObject();
			json.putBoolean("success", false);
			json.putString("message", "Unknown format: "+format);
			response.getWriter().println(json);
			return;
		}
	}

	/**
	 * Produce a format that the d3 statistic charts understand (e.g. Sunburst diagram)
	 * 
	 * @param session session
	 * @param db database
	 * @param sessionAsSigner session as signer
	 * @param req HTTP request
	 * @param response HTTP response
	 * @param view view to read data
	 * @throws Exception in case of errors
	 */
	private void serviceD3StatisticViewData(Session session, Database db, Session sessionAsSigner,
			HttpServletRequest req, HttpServletResponse response, VirtualView view) throws Exception {

		VirtualViewNavigator nav = view
				.createViewNav()
				.withCategories()
				.withEffectiveUserName(session.getEffectiveUserName())
				.dontShowEmptyCategories()
				.build();

		nav.expandAll();
		VirtualViewEntryData root = view.getRoot();
		
		Map<String,Class<?>> dataTypes = new HashMap<>();
		dataTypes.put("@descendantcount=>value", Integer.class);
		dataTypes.put("@categoryvalue=>name", Integer.class);

		//read first two levels of the category structure
		JsonJavaObject rootJson = processCategoryAndChildren(view, nav, root, dataTypes, 0, 2);
		response.getWriter().println(rootJson);
	}

	private JsonJavaObject processCategoryAndChildren(VirtualView view, VirtualViewNavigator nav, VirtualViewEntryData categoryEntry,
			Map<String,Class<?>> dataTypes, int currLevel, int maxLevel) {
		JsonJavaObject json = toJson(view, nav, categoryEntry, dataTypes);
		
		JsonJavaArray childrenArr = new JsonJavaArray();
		if (currLevel < maxLevel) {
			nav
			.childCategories(categoryEntry, false)
			.map((currChildEntry) -> {
				return processCategoryAndChildren(view, nav, currChildEntry, dataTypes, currLevel+1, maxLevel);
			})
			.forEach((currJson) -> {
				childrenArr.add(currJson);
			});
		}
		
		if (childrenArr.length() > 0) {
			json.putArray("children", childrenArr);
			json.remove("value");
		}

		return json;
	}

	/**
	 * Produce a format that our frontend Domino view table understands
	 * 
	 * @param session session
	 * @param db database
	 * @param sessionAsSigner session as signer
	 * @param req HTTP request
	 * @param response HTTP response
	 * @param view view to read data
	 * @throws Exception in case of errors
	 */
	private void serviceCategorizedViewData(Session session, Database db, Session sessionAsSigner, HttpServletRequest req,
			HttpServletResponse response, VirtualView view) throws Exception {
		
		int skip = 0;
		try {
			String sSkip = req.getParameter("skip");
			if (StringUtil.isNotEmpty(sSkip)) {
				skip = Integer.parseInt(sSkip);
			}
		}
		catch (NumberFormatException e) {
			e.printStackTrace();
		}
		
		long limit = Long.MAX_VALUE;
		try {
			String sLimit = req.getParameter("limit");
			if (StringUtil.isNotEmpty(sLimit)) {
				limit = Long.parseLong(sLimit);
			}
		}
		catch (NumberFormatException e) {
			e.printStackTrace();
		}
		final long fLimit = limit;
				
		VirtualViewNavigatorBuilder navBuilder = view
				.createViewNav()
				.withCategories()
				.withDocuments()
				.withEffectiveUserName(session.getEffectiveUserName())
				.dontShowEmptyCategories();

		String topLevelCategory = req.getParameter("toplevelcategory");
		
		VirtualViewNavigator nav;
		if (StringUtil.isEmpty(topLevelCategory)) {
			nav = navBuilder.build();
		}
		else {
			nav = navBuilder.buildFromCategory(topLevelCategory);
		}
		
		boolean expandAll = "true".equalsIgnoreCase(req.getParameter("expandall"));
		if (expandAll) {
			nav.expandAll();
		}
		else {
			nav.collapseAll();
		}
		
		String[] expandCollapseNoteIds = req.getParameterValues("expand");
		
		if (expandCollapseNoteIds != null) {
			int[] noteIds = Arrays.asList(expandCollapseNoteIds)
			.stream()
			.mapToInt((sNoteId) -> {
				return Integer.parseInt(sNoteId);
			})
			.toArray();
			
			if (expandAll) {
				for (int currNoteId : noteIds) {
					nav.collapse("virtualview", currNoteId);
				}
			}
			else {
				for (int currNoteId : noteIds) {
					nav.expand("virtualview", currNoteId);
				}
			}
		}

		JsonJavaArray jsonArr = new JsonJavaArray();
		
		AtomicBoolean hasMoreRows = new AtomicBoolean();
		
		Stream<VirtualViewEntryData> entries;
		String startPos = req.getParameter("startpos");
		if (StringUtil.isEmpty(startPos) || "1".equals(startPos)) {
			entries = nav.entriesForward(SelectedOnly.NO);
		}
		else {
			entries = entriesForwardFromPosition(nav, startPos);
		}
		
		Map<String,Class<?>> dataTypes = new HashMap<>();
		dataTypes.put("firstname", String.class);
		dataTypes.put("lastname", String.class);
		dataTypes.put("@categoryvalue", String.class);
		dataTypes.put("@descendantcount", Integer.class);
		
		entries
		.skip(skip)
		.limit(limit == Long.MAX_VALUE ? Long.MAX_VALUE : limit + 1) // read one more row for "hasmore" flag
		.map((entry) -> {
			if (jsonArr.length() < fLimit) {
				return toJson(view, nav, entry, dataTypes);
			}
			else {
				hasMoreRows.set(Boolean.TRUE);
				return null;
			}
		})
		.filter(Objects::nonNull)
		.forEach(jsonArr::add);
		
		JsonJavaObject json = new JsonJavaObject();
		json.putArray("items", jsonArr);
		json.putBoolean("hasmore", hasMoreRows.get());
		
		response.getWriter().println(json);
	}

	private JsonJavaObject toJson(VirtualView view, VirtualViewNavigator nav, VirtualViewEntryData entry, Map<String,Class<?>> dataTypes) {
		JsonJavaObject json = new JsonJavaObject();
		json.putString("@pos", entry.getPositionStr());
		json.putString("@origin", entry.getOrigin());
		json.putInt("@noteid", entry.getNoteId());
		json.putString("@unid", entry.getUNID());
		
		if (entry.isCategory()) {
			json.putBoolean("@iscategory", true);
			
			json.put("@isexpanded", nav.isExpanded(entry));
			json.put("@indentlevels", entry.getIndentLevels());
		}
		else {
			json.putBoolean("@iscategory", false);
		}
		if (entry.isDocument()) {
			json.putBoolean("@isdocument", true);
		}
		else {
			json.putBoolean("@isdocument", false);
		}
		
		for (Entry<String,Class<?>> currTypeEntry : dataTypes.entrySet()) {
			String currItemName = currTypeEntry.getKey();
			String currJsonName;
			
			int iPos = currItemName.indexOf("=>");
			if (iPos != -1) {
				currJsonName = currItemName.substring(iPos+2);
				currItemName = currItemName.substring(0, iPos);
			}
			else {
				currJsonName = currItemName;
			}
			Class<?> currType = currTypeEntry.getValue();
			
			if ("@childcount".equalsIgnoreCase(currItemName)) {
				json.putInt(currJsonName, entry.getChildCount());
			}
			else if ("@descendantcount".equalsIgnoreCase(currItemName)) {
				json.putInt(currJsonName, entry.getDescendantCount());
			}
			else if ("@categoryvalue".equalsIgnoreCase(currItemName)) {
				if (entry.isCategory() && !view.getRoot().equals(entry)) {
					Object catValue = entry.getCategoryValue();
					if (catValue instanceof String) {
						json.putString(currJsonName, (String) catValue);
					}
					else if (catValue instanceof Integer) {
						json.putInt(currJsonName, (Integer) catValue);
					}
					else if (catValue instanceof Long) {
						json.putLong(currJsonName, (long) catValue);
					}
					else if (catValue instanceof Double) {
						json.putDouble(currJsonName, (Double) catValue);
					}
					else if (catValue instanceof NotesTimeDate) {
						Optional<String> iso8601 = toISO8601((NotesTimeDate) catValue);
						if (iso8601.isPresent()) {
							json.putString(currJsonName, iso8601.get());
						}
					}
				}
			}
			else {
				if (String.class.equals(currType)) {
					String sVal = entry.getAsString(currItemName, null);
					if (sVal != null) {
						json.putString(currJsonName, sVal);
					}
				}
				else if (String[].class.equals(currType)) {
					List<String> sVal = entry.getAsStringList(currItemName, null);
					if (sVal != null) {
						json.putArray(currJsonName, new JsonJavaArray((List) sVal));
					}
				}
				else if (Boolean.class.equals(currType)) {
					Integer iVal = entry.getAsInteger(currItemName, null);
					if (iVal != null) {
						json.putBoolean(currJsonName, iVal.intValue() == 1);
					}
				}
				else if (Integer.class.equals(currType)) {
					Integer iVal = entry.getAsInteger(currItemName, null);
					if (iVal != null) {
						json.putInt(currJsonName, iVal.intValue());
					}				
				}
				else if (Long.class.equals(currType)) {
					Long lVal = entry.getAsLong(currItemName, null);
					if (lVal != null) {
						json.putLong(currJsonName, lVal.longValue());
					}
				}
				else if (Double.class.equals(currType)) {
					Double dVal = entry.getAsDouble(currItemName, null);
					if (dVal != null) {
						json.putDouble(currJsonName, dVal.longValue());
					}
				}
				else if (Integer[].class.equals(currType)) {
					List<Integer> iVal = entry.getAsIntegerList(currItemName, null);
					if (iVal != null) {
						json.putArray(currJsonName, new JsonJavaArray((List) iVal));
					}
				}
				else if (Long[].class.equals(currType)) {
					List<Long> lVal = entry.getAsLongList(currItemName, null);
					if (lVal != null) {
						json.putArray(currJsonName, new JsonJavaArray((List) lVal));
					}				
				}
				else if (Double[].class.equals(currType)) {
					List<Double> dVal = entry.getAsDoubleList(currItemName, null);
					if (dVal != null) {
						json.putArray(currJsonName, new JsonJavaArray((List) dVal));
					}				
				}
				else if (NotesTimeDate.class.equals(currType)) {
					NotesTimeDate ndtVal = entry.getAsTimeDate(currItemName, null);
					if (ndtVal != null) {
						Optional<String> iso8601 = toISO8601(ndtVal);
						if (iso8601.isPresent()) {
							json.putString(currJsonName, iso8601.get());
						}
					}
				}
				else if (NotesTimeDate[].class.equals(currType)) {
					List<NotesTimeDate> ndtVal = entry.getAsTimeDateList(currItemName, null);
					if (ndtVal != null) {
						JsonJavaArray jsonArr = new JsonJavaArray();
						
						ndtVal
						.stream()
						.map(this::toISO8601)
						.forEach(jsonArr::add);
						
						json.putArray(currJsonName, jsonArr);
					}				
				}
			}
		}
		return json;
	}
	
	private Optional<String> toISO8601(NotesTimeDate catValue) {
		return catValue.toTemporal()
		.map((t) -> {
			if (t instanceof OffsetDateTime) {
				return ((OffsetDateTime)t).format(DateTimeFormatter.ISO_DATE_TIME);
			}
			else if (t instanceof LocalDate) {
				return ((LocalDate)t).format(DateTimeFormatter.ISO_DATE_TIME);
			}
			else if (t instanceof LocalTime) {
				return ((LocalTime)t).format(DateTimeFormatter.ISO_DATE_TIME);
			}
			else {
				return null;
			}
		})
		.filter(Objects::nonNull);
	}

	public Stream<VirtualViewEntryData> entriesForwardFromPosition(VirtualViewNavigator nav,  String startPos) {
		if (!nav.gotoPos(startPos)) {
			return Stream.empty();
		}
		
		return StreamSupport
				.stream(new Spliterators.AbstractSpliterator<VirtualViewEntryData>(Long.MAX_VALUE, Spliterator.ORDERED) {
					@Override
					public boolean tryAdvance(Consumer<? super VirtualViewEntryData> action) {
						VirtualViewEntryData entry = nav.getCurrentEntry();
						if (entry != null) {
							action.accept(entry);
						}
						return nav.gotoNext();
					}
				}, false);
	
	}
}
