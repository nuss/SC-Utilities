AbstractSNSampler {
	classvar <>synthDescLib = \SN, <>oscFeedbackAddr;

	// handle widget creation in CVCenter
	cvCenterAddWidget { |suffix="", value, spec, func, midiMode, softWithin|
		var wdgtName = (this.name.asString ++ suffix).asSymbol;
		CVCenter.all[wdgtName] ?? {
			CVCenter.use(wdgtName, spec, value, this.name);
			if (func.class == String) {
				func = func.interpret;
			};
			if (func.isFunction) {
				CVCenter.addActionAt(wdgtName, suffix, func);
			}
		};

		switch (CVCenter.cvWidgets[wdgtName],
			CVWidgetKnob, {
				midiMode !? { CVCenter.cvWidgets[wdgtName].setMidiMode(midiMode) };
				softWithin !? { CVCenter.cvWidgets[wdgtName].setSoftWithin(softWithin) };
			},
			CVWidget2D, {
				#[lo, hi].do{ |sl|
					midiMode !? { CVCenter.cvWidgets[wdgtName].setMidiMode(midiMode, sl) };
					softWithin !? { CVCenter.cvWidgets[wdgtName].setSoftWithin(softWithin, sl) };
				}
			},
			CVWidgetMS, {
				CVenter.at(wdgtName).spec.size.do { |i|
					midiMode !? { CVCenter.cvWidgets[wdgtName].setMidiMode(midiMode, i) };
					softWithin !? { CVCenter.cvWidgets[wdgtName].setSoftWithin(softWithin, i) };
				}
			}
		)

		^CVCenter.cvWidgets[wdgtName];
	}
}