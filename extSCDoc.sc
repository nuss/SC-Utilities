+SCDoc {
	*renderAllSync {|includeExtensions=true, doneAction|
		this.postMsg("Rendering all documents");
		this.documents.do {|doc|
			if(doc.oldHelp.isNil and: {includeExtensions or: {doc.isExtension.not}}) {
				if(doc.isUndocumentedClass) {
					this.renderUndocClass(doc);
				} {
					this.parseAndRender(doc);
				}
			}
		};
		this.postMsg("Done!");
		doneAction !? { doneAction.value };
	}

	*rebuildHelp {
		this.renderAllSync(doneAction: { this.indexAllDocuments(true) });
	}
}