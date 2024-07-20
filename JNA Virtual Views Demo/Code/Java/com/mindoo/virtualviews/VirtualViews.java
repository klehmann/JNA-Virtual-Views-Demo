package com.mindoo.virtualviews;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.mindoo.domino.jna.INoteSummary;
import com.mindoo.domino.jna.IViewColumn.ColumnSort;
import com.mindoo.domino.jna.utils.StringUtil;
import com.mindoo.domino.jna.virtualviews.VirtualView;
import com.mindoo.domino.jna.virtualviews.VirtualViewColumn;
import com.mindoo.domino.jna.virtualviews.VirtualViewColumn.Category;
import com.mindoo.domino.jna.virtualviews.VirtualViewColumn.Hidden;
import com.mindoo.domino.jna.virtualviews.VirtualViewColumn.Total;
import com.mindoo.domino.jna.virtualviews.VirtualViewColumnValueFunction;
import com.mindoo.domino.jna.virtualviews.VirtualViewFactory;
import com.mindoo.domino.jna.virtualviews.VirtualViewNavigator;

public enum VirtualViews {
INSTANCE;

	Map<String,String> companyContinents;

	private VirtualViews() {		
	}
	
	public Optional<VirtualView> getViewById(String id) {
		VirtualView view = null;
		
		if ("fakenames_bycontinent_categorized".equals(id)) {
			
			view = VirtualViewFactory.INSTANCE.createViewOnce(id,
					17, // view version number
					10, TimeUnit.MINUTES, // keep in memory for 10 minutes
					(viewId) -> {

				return VirtualViewFactory.createView(
						
						// use a Java function to compute the "Continent" category:
						
						new VirtualViewColumn("Continent", "$1", Category.YES, Hidden.NO, ColumnSort.ASCENDING, Total.NONE,
								new VirtualViewColumnValueFunction<String>(1) {
							
							@Override
							public String getValue(String origin, String itemName, INoteSummary columnValues) {
								String companyName = columnValues.getAsString("CompanyName", "");
								
								Map<String,String> lookupMap = getCompanyContinentMap();
								return lookupMap.getOrDefault(companyName, "Unknown");
							}							
						}),

						// use formula language for the remaining columns:
						
						new VirtualViewColumn("Company Name", "$2", Category.YES, Hidden.NO, ColumnSort.ASCENDING, Total.NONE,
						"@Left(CompanyName;1) + \"\\\\\" + CompanyName"),

						new VirtualViewColumn("Lastname", "Lastname", Category.NO, Hidden.NO, ColumnSort.ASCENDING, Total.NONE,
								"Lastname"),

						new VirtualViewColumn("Firstname", "Firstname", Category.NO, Hidden.NO, ColumnSort.ASCENDING, Total.NONE,
								"Firstname"),

						new VirtualViewColumn("CompanyName", "CompanyName", Category.NO, Hidden.YES, ColumnSort.NONE, Total.NONE,
								"CompanyName")

						)
						// load data from two fakenames databases
						.withDbSearch("fakenames1", "", "fakenames.nsf", "Form=\"Person\"")
						.withDbSearch("fakenames2", "", "fakenames2.nsf", "Form=\"Person\"")
						.build();
			});
		}

		return Optional.ofNullable(view);
	}
	
	/**
	 * Produces a fake lookup map for the company continents
	 * 
	 * @return map with continents by company name
	 */
	private Map<String,String> getCompanyContinentMap() {
		if (companyContinents != null) {
			return companyContinents;
		}
		
		// build a view categorized by company name
		
		VirtualView viewCompanies = VirtualViewFactory.INSTANCE.createViewOnce("companyNames", 1, 10, TimeUnit.MINUTES,
				(viewId) -> {
			
					return VirtualViewFactory.createView(
							new VirtualViewColumn("Company Name", "CompanyName", Category.YES, Hidden.NO, ColumnSort.ASCENDING, Total.NONE,
									"CompanyName")

							)
							.withDbSearch("fakenames1", "", "fakenames.nsf", "Form=\"Person\"")
							.withDbSearch("fakenames2", "", "fakenames2.nsf", "Form=\"Person\"")
							.build();
		});
		
		// go through category entries and pick a continent for each company name
		
		VirtualViewNavigator nav = viewCompanies.createViewNav()
				.dontShowEmptyCategories()
				.withCategories()
				.build();
		
		companyContinents = new HashMap<>();
		
		nav
		.childCategories(viewCompanies.getRoot(), false)
		.forEach((entry) -> {
			Object companyName = entry.getCategoryValue();
			
			if (companyName instanceof String && StringUtil.isNotEmpty((String) companyName)) {
				String continent;
				
				int mod = companyName.hashCode() % 6;
				switch (mod) {
				case 0:
					continent = "North America";
					break;
				case 1:
					continent = "Europe";
					break;
				case 2:
					continent = "Asia";
					break;
				case 3:
					continent = "South America";
					break;
				case 4:
					continent = "Africa";
					break;
				case 5:
					continent = "Australia";
					break;
				default:
					continent = "Unknown";
				}
				
				companyContinents.put(companyName.toString(), continent);
			}
		});
		
		return companyContinents;
	}
}
