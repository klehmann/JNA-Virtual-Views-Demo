const sunburstBuilder = (async (options) => {

	async function fetchData() {
		try {
			const params = new URLSearchParams();
			params.append("viewid", options.viewid);
			params.append("format", "d3stats");
			
	    	const response = await fetch(options.url, {
				method: "POST",
				body: params
			});
	
		    if (!response.ok) {
		      throw new Error(`Response status: ${response.status}`);
		    }
	
	   		const json = await response.json();
			console.log(json);
			return json;
		} catch (error) {
			console.error(error.message);
			throw new Error(error.message);
		}
	}

	const data = await fetchData();
	const chartNode = sunburst(data, {
        onClick : options.onClick
	});
	
	return chartNode;
});

