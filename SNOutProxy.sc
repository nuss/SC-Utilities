SNOutProxy {
	classvar <all, num = 1;
	var <proxy;

	*new { |numInChannels=2|
		^super.new.init(numInChannels)
	}

	init { |numChans|
		all ?? { all = () };

		proxy = Ndef(("out" ++ num).asSymbol, {
			var sig = Splay.ar(
				\in.ar(0!numChans),
				\spread.kr(0.5),
				1,
				\center.kr(0)
			);
			Compander.ar(
				sig, sig,
				\thresh.kr(1),
				\slopeBelow.kr(0.3),
				\slopeAbove.kr(1),
				\clampTime.kr(0.001),
				\relaxTime.kr(0.2),
				\amp.kr(0)
			)
		});

		CVCenter.use(("spread out" ++ num).asSymbol, value: 0.5, tab: ("out" ++ num).asSymbol);
		CVCenter.addActionAt(("spread out" ++ num).asSymbol, 'set spread out', { |cv|
			Ndef(("out" ++ num).asSymbol).set(\spread, cv.value)
		});
		CVCenter.use(("center out" ++ num).asSymbol, \pan, tab: ("out" ++ num).asSymbol);
		CVCenter.addActionAt(("center out" ++ num).asSymbol, 'set center out', { |cv|
			Ndef(("out" ++ num).asSymbol).set(\center, cv.value)
		});
		CVCenter.use(("thresh compressor" ++ num).asSymbol, value: 1, tab: ("out" ++ num).asSymbol);
		CVCenter.addActionAt(("thresh compressor" ++ num).asSymbol, 'set threshold compressor', { |cv|
			Ndef(("out" ++ num).asSymbol).set(\thresh, cv.value)
		});
		CVCenter.use(("slopeBelow compressor" ++ num).asSymbol, value: 0.3, tab: ("out" ++ num).asSymbol);
		CVCenter.addActionAt(("slopeBelow compressor" ++ num).asSymbol, 'set slopeBelow compressor', { |cv|
			Ndef(("out" ++ num).asSymbol).set(\slopeBelow, cv.value)
		});
		CVCenter.use(("slopeAbove compressor" ++ num).asSymbol, #[0.0, 2.0], value: 1, tab: ("out" ++ num).asSymbol);
		CVCenter.addActionAt(("slopeAbove compressor" ++ num).asSymbol, 'set slopeAbove compressor', { |cv|
			Ndef(("out" ++ num).asSymbol).set(\slopeAbove, cv.value)
		});
		CVCenter.use(("clampTime compressor" ++ num).asSymbol, #[0.001, 0.1], value: 0.001, tab: ("out" ++ num).asSymbol);
		CVCenter.addActionAt(("clampTime compressor" ++ num).asSymbol, 'set clampTime compressor', { |cv|
			Ndef(("out" ++ num).asSymbol).set(\clampTime, cv.value)
		});
		CVCenter.use(("relaxTime compressor" ++ num).asSymbol, #[0.002, 0.2], value: 0.2, tab: ("out" ++ num).asSymbol);
		CVCenter.addActionAt(("relaxTime compressor" ++ num).asSymbol, 'set relaxTime compressor', { |cv|
			Ndef(("out" ++ num).asSymbol).set(\relaxTime, cv.value)
		});
		CVCenter.use(("amp proxy" ++ num).asSymbol, \amp, tab: ("out" ++ num).asSymbol);
		CVCenter.addActionAt(("amp proxy" ++ num).asSymbol, 'set proxy volume', { |cv|
			Ndef(("amp proxy" ++ num).asSymbol).set(\amp, cv.value)
		});

		all.put(("out" ++ num).asSymbol, Ndef(("out" ++ num).asSymbol));
		num = num + 1;
	}

	clear {
		CVCenter.removeAtTab(proxy.key);
		all[proxy.key].clear;
		all.removeAt(proxy.key);
	}

	play { |server|
		server ?? { server = Server.default };
		server.waitForBoot {
			proxy.play;
		}
	}


}