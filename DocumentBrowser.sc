
DocumentBrowser {
	classvar <window, <widthViews, <listView;
	classvar <browserBounds, <leftBounds, <rightBounds, <postBounds;
	classvar <docs, <task;
	classvar <>dt=0.1;
	classvar <docStack;

	*pushDocStack { | doc |
		// stack backs up to the previous document viewed when closing a document
		if (doc != Document.listener) {
			docStack = docStack.add(doc);
			if (docStack.size > 10) { docStack =  docStack[1..10] };
		}
	}
	
	*determineBounds {
		var screenWidth, spacing;
		var browserBounds, l, t, w, h; 
		spacing = 10;
		browserBounds = window.bounds;
		screenWidth = Window.screenBounds.width - spacing;
		l = browserBounds.left + browserBounds.width + spacing;
		t = browserBounds.top; 		
		h = browserBounds.height + 20;	// include title bar in bounds
		
		// constrain document bounds to screen but keep width >= 100
		
		w = widthViews[0].value;
		w = l + w min: screenWidth - l max: 100;
		leftBounds = Rect(l,t,w,h);
		l = l + w + spacing min: (screenWidth - 100);
		w = widthViews[1].value;
		w = l + w + spacing min: screenWidth - l max: 100;
		rightBounds = Rect(l,t,w,h);
		Archive.global.put(\documentBrowserRects, [window.bounds, leftBounds, rightBounds, Document.listener.bounds]);
	}
		
	*getDocs  {
		// collect and sort all documents
		var i, doc, oldDocs, newDocs;
//		("current document index:"+listView.value).postln;
		listView.value !? { doc = docs[listView.value] };
		oldDocs = docs;
		docs = Document.allDocuments.sort { | a , b | a.title < b.title}.copy;
//		("the docs are:"+docs).postln
		listView.items = docs.collect({ |d| if(d.isEdited, { "*"+d.title }, { d.title }) });
//		("the list should be:"+listView.items).postln;
		docStack = docStack.reject({ | d | docs.indexOf(d).isNil});
		newDocs = docs.difference(oldDocs);
		if (newDocs.size !=0) { 
			this.pushDocStack(newDocs.last);
			newDocs.do { | d | d.bounds_(leftBounds) };
		};
		doc = docStack.last;
		i = docs.indexOf(doc);
		listView.value_(i); 
	}
	
	*makeBrowserWindow {
		var l, t, w, h;
		window = Window("docs",Window.screenBounds.width_(150)).userCanClose_(false).front; 
		window.onClose_({ this.stop });
		
		StaticText(window, Rect(7, 0, 33, 20)).string_("lwidth");
		StaticText(window, Rect(75, 0, 33, 20)).string_("rwidth");
		widthViews = [
			NumberBox(window, Rect(40, 0, 30, 20)).value_(800),
			NumberBox(window, Rect(108, 0, 30, 20)).value_(500)
		];
		widthViews.do { | v |
			v.background_(Color.gray(0.8)).typingColor_(Color.red(0.5))
		};
		
		listView = ListView(window, Rect(2, 25, window.bounds.width - 6, Window.screenBounds.height - 45) )
			.items_([])
			.background_(Color.clear)
			.hiliteColor_(Color.green)
			.resize_(5)		
		;
		
		listView.action_({ | view |
			var doc; 
			if ( (doc = docs[view.value]).notNil) { 
//				doc.unfocusedFront;
				doc.front;
				this.pushDocStack(doc);
			};
		});
	
		listView.keyDownAction =  { arg view, char, modifiers, unicode, keycode;
			if (char == $-) { docs[view.value].close };
			if (unicode == 16rF700, { view.valueAction = view.value - 1;  });
			if (unicode == 16rF701, { view.valueAction = view.value + 1;  });
			if (unicode == 16rF703) { 
				if ((modifiers & 0x20) != 0) {
					docs[listView.value].bounds_(rightBounds).front;
				}{
					docs[listView.value].bounds_(rightBounds).unfocusedFront;
				};
			};
			
			if (unicode == 16rF702) { 
				if ((modifiers & 0x20) != 0) {
					docs[listView.value].bounds_(leftBounds).front;
				}{
					docs[listView.value].bounds_(leftBounds).unfocusedFront;
				};
			};
		};
	}		
	
	*gui {	 
		var rects, t, h, offset, current, listener = Document.listener;
		rects = Archive.global.at(\documentBrowserRects);
		this.makeBrowserWindow;
		current = Document.current;
		docStack = [current];
		// wait for the dust to settle...
		Routine({
			dt.wait;
			if (rects.notNil) {
				#browserBounds, leftBounds, rightBounds, postBounds = rects;
				window.bounds = browserBounds;
				widthViews[0].value = leftBounds.width;
				widthViews[1].value = rightBounds.width;
			} {	
				this.determineBounds;
			};
			docs = docStack = [Document.listener];
			this.getDocs;
	
			docs.do { | doc |
				if (doc!= listener) { 
					doc.bounds_(leftBounds) 
				} {
					doc.bounds_(postBounds? rightBounds);
				}
			};
				
//			dt = 1/30;
			this.play;
		}).play(AppClock);		
		CmdPeriod.add(this);
		current !? { current.front };
	}
	
	*play { 
		task = Routine({ 
			var oldDocs, newDocs, bounds, widths, oldL, oldR, listener, doc, index;
			loop { 
			// check for added or deleted documents
				if ( (docs != Document.allDocuments)) { 
					this.getDocs;
					docStack.last.front;
					window.refresh;
				} {
					// check for renamed documents
					if (listView.items != Document.allDocuments.collect(_.title) ) {
						oldDocs = docs;
						doc = docs[listView.value];
						this.getDocs;
						if ( (index = docs.indexOf(doc)).notNil ) {
							listView.value_(index) };
						window.refresh;
					};
				};
				// check for change in document positioning
				if ( (bounds != window.bounds) 
					||	(widths != widthViews.collect(_.value)) 
					|| 	(postBounds != Document.listener.bounds) ) {
					bounds = window.bounds;
					widths = widthViews.collect(_.value);
					oldL = leftBounds;
					oldR = rightBounds;
					this.determineBounds;
					listener = Document.listener;
					docs.do {| d|
						if (d.bounds == oldL) { 
							d.bounds_(leftBounds)
						} {
							if (d.bounds == oldR) {
								d.bounds_(rightBounds);
							}
						}
					}					
				};
				dt.wait 
			} 
		});
		task.play(AppClock);
		CmdPeriod.add(this);
	}
	
	*cmdPeriod {
		task.play(AppClock);
	}
	
	*stop { 
		task.stop; 
		CmdPeriod.remove(this);
	}
	
	*reset {
		Archive.global[\documentBrowserRects] = nil;
		postBounds = rightBounds;
		this.determineBounds
	}

}
