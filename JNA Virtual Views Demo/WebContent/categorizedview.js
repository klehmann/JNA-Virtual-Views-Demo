const viewBuilder = (async (options) => {
	const pageSize = options.pageSize || 200;
	let expandedCollapsedNoteIds = {};
	let isExpandAll = false;
	let hasMore = false;
	let topLevelCategory = null;
	
	async function fetchData(startPos, skip, limit) {
		try {
			const params = new URLSearchParams();
		    params.append("startpos", startPos);
		    params.append("skip", skip);
		    params.append("limit", limit);
			params.append("expandall", isExpandAll);
			params.append("viewid", options.viewid);
			if (topLevelCategory) {
				params.append("toplevelcategory", topLevelCategory);
			}
			for (const noteId in expandedCollapsedNoteIds) {
				params.append("expand", noteId);
			}

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

	function isExpanded(noteId) {
		if (isExpandAll) {
			return !expandedCollapsedNoteIds.hasOwnProperty(noteId);
		}
		else {
			return expandedCollapsedNoteIds.hasOwnProperty(noteId);
		}
	}
	
	function toggle(noteId) {
		if (isExpanded(noteId)) {
			return collapse(noteId);
		}
		else {
			return expand(noteId);
		}
	}
	
	function expandAll() {
		if (isExpandAll) {
			return false;
		}
		else {
			isExpandAll = true;
			expandedCollapsedNoteIds = {};
			return true;
		}
	}

	function collapseAll() {
		if (!isExpandAll) {
			return false;
		}
		else {
			isExpandAll = false;
			expandedCollapsedNoteIds = {};
			return true;
		}
	}
	
	function expand(noteId) {
		let changed = false;

		if (isExpandAll) {
			if (expandedCollapsedNoteIds.hasOwnProperty(noteId)) {
				delete expandedCollapsedNoteIds[noteId];
				changed = true;
			}
		}
		else {
			if (!expandedCollapsedNoteIds.hasOwnProperty(noteId)) {
				expandedCollapsedNoteIds[noteId] = true;
				changed = true;
			}
		}

		return changed;
	}

	function collapse(noteId) {
		if (isExpandAll) {
			if (!expandedCollapsedNoteIds.hasOwnProperty(noteId)) {
				expandedCollapsedNoteIds[noteId] = true;
				changed = true;
			}
		}
		else {
			if (expandedCollapsedNoteIds.hasOwnProperty(noteId)) {
				delete expandedCollapsedNoteIds[noteId];
				changed = true;
			}
		}

		return changed;
	}
	
	const cols = options.cols || [];

	const table = document.createElement('table');
	table.classList.add('dominoview-container');
	table.classList.add('table');
	table.classList.add('table-bordered');
	
	const thead = document.createElement('thead');
	table.appendChild(thead);
	
	const thead_tr = document.createElement('tr');
	thead.appendChild(thead_tr);
	
	for (let i=0; i<cols.length; i++) {
		const currTh = document.createElement('th');
		currTh.setAttribute('data-itemname', cols[i].id);
		thead_tr.appendChild(currTh);
		
		currTh.appendChild(document.createTextNode(cols[i].title));
	}
	
	const tbody = document.createElement('tbody');
	table.appendChild(tbody);
	
	async function loadMoreRows() {
		if (!hasMore) {
			return;
		}
		
		const existingRows = tbody.querySelectorAll('tr');
		if (existingRows.length > 0) {
			const lastPos = existingRows[existingRows.length-1].getAttribute('data-pos');
			const result = await fetchData(lastPos, 1, pageSize);
			hasMore = result.hasmore;
			addRowsToTable(result.items);
		}
	}
	
	const api = {
		isExpanded,
		loadMoreRows,
		expandAll,
		collapseAll,
		reload,
		setTopLevelCategory
	};
	
	async function setTopLevelCategory(category) {
		topLevelCategory = category;
		await reload();
	}
	
	async function reload() {
		tbody.innerHTML = '';
		const result = await fetchData('1', 0, pageSize);
		hasMore = result.hasmore;
		addRowsToTable(result.items);		
	}
	
	function addRowsToTable(arrRows, optStartPos) {
		if (optStartPos) {
			// Find the row with matching data-pos attribute
	        const existingRows = tbody.querySelectorAll('tr');
	        const existingRowsLength = existingRows.length;
	        
	        let startIndex = -1;
	
	        for (let i = 0; i < existingRows.length; i++) {
	            if (existingRows[i].getAttribute('data-pos') === optStartPos) {
	                startIndex = i;
	                break;
	            }
	        }
	
	        // If found, remove the matching row and all following rows
	        if (startIndex !== -1) {
	        	for (let i=startIndex; i<existingRowsLength; i++) {
	        		tbody.removeChild(existingRows[i]);
	        	}
	        }
 			
		}
		
		for (let i=0; i<arrRows.length; i++) {
			const tbody_tr = document.createElement('tr');
			tbody_tr.setAttribute('data-origin', arrRows[i]['@origin']);
			tbody_tr.setAttribute('data-noteid', arrRows[i]['@noteid']);
			tbody_tr.setAttribute('data-pos', arrRows[i]['@pos']);
			tbody_tr.setAttribute('data-isdocument', arrRows[i]['@isdocument']);
			tbody_tr.setAttribute('data-iscategory', arrRows[i]['@iscategory']);
			tbody_tr.setAttribute('data-indentlevels', arrRows[i]['@indentlevels']);
			
			tbody.appendChild(tbody_tr);
			
			for (let j=0; j<cols.length; j++) {
				const currTd = document.createElement('td');
				tbody_tr.appendChild(currTd);
				currTd.setAttribute('data-itemname', cols[j].id);
				
				if (cols[j].valueHTML) {
					cols[j].valueHTML.apply(api, [arrRows[i], currTd]);
				}
				else if (cols[j].value) {
					const currTdValue = cols[j].value.apply(api, [arrRows[i]] ) || '';
					currTd.appendChild(document.createTextNode(currTdValue));
				}
			}	
		}
	}
	
	table.addEventListener("click", async (e) => {
		const tdElement = e.target.closest('td');
		const expandClicked = tdElement.getAttribute('data-itemname') === 'expandstate';
		
		if (expandClicked) {
			// Find the nearest tr element
			const trElement = e.target.closest('tr');
		  
			if (trElement) {
			    // Read the origin and noteid data attributes
			    const origin = trElement.getAttribute('data-origin');
			    const noteId = trElement.getAttribute('data-noteid');
			    const pos = trElement.getAttribute('data-pos');
			    const isCategory = trElement.getAttribute('data-iscategory') === 'true';
			    
			    console.log('Clicked row:', {
			    	pos,
			    	origin,
			    	noteId
			    });
		    
			    if (isCategory && toggle(noteId)) {
			    	console.log('toggled');
			    	
			    	const newData = await fetchData(pos, 0, pageSize);
			    	addRowsToTable(newData.items, pos);
			    }
			}
		}
	});


	setTimeout(async () => {
		const result = await fetchData('1', 0, pageSize);
		hasMore = result.hasmore;
		addRowsToTable(result.items);
	}, 0);
	
	return Object.assign(table, api);
});
