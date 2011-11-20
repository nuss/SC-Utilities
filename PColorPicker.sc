PColorPicker {
	/* create a window with knobs to set the background color of the PoppschLiveObjects*/
	*new {
		var text, list, knobs = Array.newClear(4), initColor = [0.8,0.8,0.8,1], items, w, color, colorO;
		w = GUI.window.new(this.name, Rect(500,500, 210, 100));
		w.view.decorator = FlowLayout(w.view.bounds);
		w.view.background = Color.fromArray(initColor);
		knobs.size.do{|i|
			knobs[i] = GUI.knob.new(w, Rect(0, 0, 30,30))
				.step_(0.005)
				.canFocus_(false)
				.action_({|elem|
//					var color, colorO;					color = initColor;
					color[i] = elem.value;
					initColor = color;					colorO = Color.fromArray(color);
					text.object = Color.fromArray(color);
					w.view.background_(colorO);
				})
			;
			knobs[i].value = initColor[i];
		};
		w.view.decorator.nextLine;

		text = GUI.dragSource.new(w, Rect(0,0,200,25));
		w.view.decorator.nextLine;
		
		w.front;
	}
}                