SNLooper : AbstractSNSampler {
	classvar <all;
	var <name, <clock, <numBuffers, <bufLength, <numChannels, <>randomBufferSelect, <server;
	var <buffers, recorder, <loopLengths;
	var <sample = false, samplingController, samplingModel, onTime, offTime;
	var loopCVs, usedBuffers;
	var <>debug = false;

	*new { |name, clock, numBuffers, bufLength, numChannels, randomBufferSelect, server, oscFeedbackAddr|
		^super.newCopyArgs(name, clock ? TempoClock.default, numBuffers ? 5, bufLength ? 5, numChannels ? 1, randomBufferSelect ? false, server ? Server.default).init(oscFeedbackAddr);
	}

	init { |oscFeedbackAddr|
		[name, clock, numBuffers, bufLength, numChannels, server].postln;
		oscFeedbackAddr !? {
			if (oscFeedbackAddr.class !== NetAddr) {
				Error("If supplied, oscFeedbackAddr must be a NetAddr. Given: %\n".format(oscFeedbackAddr));
			} {
				this.class.oscFeedbackAddr_(oscFeedbackAddr);
			}
		};
		all ?? { all = () };
		if (name.isNil) {
			name = ("looper" + (all.size + 1)).asSymbol
		} {
			name = name.asSymbol;
			if (all.includesKey(name)) {
				Error("A looper under the name '%' already exists".format(name)).throw;
			}
		};
		all = all.put(name.asSymbol, this);
		server.waitForBoot {
			var in;
			buffers = Buffer.allocConsecutive(numBuffers, server, bufLength * server.sampleRate, numChannels);
			loopLengths = 0 ! numBuffers;
			usedBuffers = false ! numBuffers;
			server.sync;
			recorder = NodeProxy.audio(server, numChannels).pause;
			if (numChannels < 2) { in = 0 } { in = 0!numChannels };
			recorder[0] = {
				var soundIn = SoundIn.ar(\in.kr(in)).scope("looper in");
				BufWr.ar(
					soundIn,
					\bufnum.kr(0),
					Phasor.ar(\trig.tr(0), BufRateScale.kr(\bufnum.kr(buffers[0].bufnum)), 0, BufFrames.kr(\bufnum.kr(0)))
				);
				// play silently
				// Silent.ar;
				soundIn * \bypassAmp.kr(1);
			};
			CVCenter.scv.loopers ?? {
				CVCenter.scv.loopers = ();
				CVCenter.scv.loopers.put(name, (looper: this, recorder: recorder));
			};
			this.createWidgets;
			this.prInitSampler;
			this.prSetUpLoops;
		}
	}

	createWidgets {
		this.cvCenterAddWidget("-start/stop", 0, #[0, 1, \lin, 1, 0], "{ |cv|
			var samplingActive = cv.value.asBoolean;
			if ((samplingActive and: { CVCenter.scv.loopers['%'].looper.sample.not }).or(
				samplingActive.not and: { CVCenter.scv.loopers['%'].looper.sample }
			)) {
				CVCenter.scv.loopers['%'].looper.sample_(samplingActive)
			}
		}".format(name, name, name), 0, 0);
		this.cvCenterAddWidget("-in", 0, \in, "{ |cv|
			CVCenter.scv.loopers['%'].recorder.set(\\in, cv.value)
		}".format(name));
		// this.cvCenterAddWidget("-set bufnum", 0, [0, numBuffers - 1, \lin, 1, 0], "{ |cv|
		// 	CVCenter.scv.loopers['%'].recorder.set(\\bufnum, CVCenter.scv.loopers['%'].buffers[cv.value.asInteger].bufnum);
		// }".format(name, name));
		this.cvCenterAddWidget("-set bufnum", 0, [0, numBuffers - 1, \lin, 1, 0], { |cv|
			recorder.set(\bufnum, buffers[cv.value].bufnum);
		});
	}

	prInitSampler {
		samplingModel = Ref(sample);
		samplingController = SimpleController(samplingModel);
		samplingController.put(\value, { |changer, what|
			var length, nextBuf, bufIndex;
			sample = changer.value;
			if (sample) {
				onTime = Main.elapsedTime;
				recorder.resume;
				CVCenter.at((name ++ "-start/stop").asSymbol).value_(1);
			} {
				var bufnum, amps, durs, ends;
				offTime = Main.elapsedTime;
				recorder.pause;
				// reset phasor before next sampling
				recorder.set(\trig, 1);
				bufnum = recorder.get(\bufnum);
				// bufffers may begin with other bufnums than 0,
				// so we use the index in the buffers array
				bufIndex = buffers.detectIndex{ |buf| buf.bufnum == bufnum };
				usedBuffers[bufIndex] = true;
				// reset if all buffers have been filled already
				if (usedBuffers.select { |bool| bool == true }.size == numBuffers) {
					usedBuffers = false ! numBuffers;
				};
				// usedBuffers.postln;
				length = offTime - onTime;
				// "bufIndex: %\n".postf(bufIndex);
				if (length > bufLength) {
					loopLengths[bufIndex] = bufLength;
				} {
					loopLengths[bufIndex] = length;
				};
				this.prSetSpecConstraints(bufIndex, loopLengths[bufIndex]);
				// "loopCVs.start.spec.maxval: %\n
				// loopCVs.end.spec.maxval: %\n
				// loopCVs.dur.spec.maxval: %\n".postf(
				// 	loopCVs.start.spec.maxval, loopCVs.end.spec.maxval, loopCVs.dur.spec.maxval
				// );
				Ndef((name ++ "Out").asSymbol).play;
				this.prSetCVValues(bufIndex);
				if (this.randomBufferSelect.not) {
					nextBuf = bufIndex + 1 % numBuffers;
				} {
					nextBuf = usedBuffers.selectIndex{ |bool| bool == false }.choose;
				};
				recorder.set(\bufnum, buffers[nextBuf].bufnum);
				CVCenter.at((name ++ "-set bufnum").asSymbol).value_(nextBuf);
				// "buffers[%].bufnum: %\n".postf(nextBuf, buffers[nextBuf].bufnum);
				onTime = nil;
				CVCenter.at((name ++ "-start/stop").asSymbol).input_(0);
				"done".postln;
			}
		})
	}

	prSetSpecConstraints { |index, length|
		loopCVs.start.spec.maxval[index] = length / bufLength;
		loopCVs.end.spec.maxval[index] = length / bufLength;
		loopCVs.dur.spec.maxval = length;
	}

	prSetCVValues { |bufIndex|
		var amps, durs, ends;
		amps = loopCVs.amp.value;
		amps[bufIndex] = 1;
		loopCVs.amp.value_(amps);
		durs = loopCVs.dur.value;
		durs[bufIndex] = loopLengths[bufIndex];
		loopCVs.dur.value_(durs);
		ends = loopCVs.end.input;
		ends[bufIndex] = 1;
		loopCVs.end.input_(ends);
	}

	sample_ { |onOff|
		samplingModel.value_(onOff).changed(\value);
	}

	recorderSetIn { |in|
		CVCenter.at((name ++ "-in").asSymbol).value_(in.asInteger);
	}

	recorderSetBufnum { |bufnum|
		// recorder.set(\bufnum, bufnum);
		CVCenter.at((name ++ "-set bufnum").asSymbol).value_(bufnum);
	}

	prSetUpLoops {
		loopCVs = ();
		loopCVs.start = CVCenter.use((name ++ "Start").asSymbol, [0, bufLength] ! numBuffers, 0, (name ++ "Loops").asSymbol);
		loopCVs.end = CVCenter.use((name ++ "End").asSymbol, [0, bufLength] ! numBuffers, bufLength, (name ++ "Loops").asSymbol);
		loopCVs.loopRate = CVCenter.use((name ++ "Rate").asSymbol, #[-2, 2] ! numBuffers, 1.0, tab: (name ++ "Loops").asSymbol);
		loopCVs.atk = CVCenter.use((name ++ "Atk").asSymbol, #[0.02, 3, \exp] ! numBuffers, tab: (name ++ "Loops").asSymbol);
		loopCVs.sust = CVCenter.use((name ++ "Sust").asSymbol, #[0.1, 1.0] ! numBuffers, 1, (name ++ "Loops").asSymbol);
		loopCVs.rel = CVCenter.use((name ++ "Rel").asSymbol, #[0.02, 3, \exp] ! numBuffers, tab: (name ++ "Loops").asSymbol);
		loopCVs.dec = CVCenter.use((name ++ "Dec").asSymbol, #[0.02, 7, \exp] ! numBuffers, tab: (name ++ "Loops").asSymbol);
		loopCVs.curve = CVCenter.use((name ++ "Curve").asSymbol, #[-4, 4] ! numBuffers, 0, (name ++ "Loops").asSymbol);
		loopCVs.dur = CVCenter.use((name ++ "Dur").asSymbol, [0.1, bufLength] ! numBuffers, bufLength, (name ++ "Loops").asSymbol);
		loopCVs.amp = CVCenter.use((name ++ "GrainAmp").asSymbol, \amp ! numBuffers, tab: (name ++ "Loops").asSymbol);

		this.initPdef;

		Ndef((name ++ "Out").asSymbol)[0] = {
			Splay.ar(\in.ar(0 ! numBuffers), (name ++ \Spread).asSymbol.kr(0.5), (name ++ \Amp).asSymbol.kr(1), (name ++ \Center).asSymbol.kr(0.0))
		};

		Spec.add((name ++ \Center).asSymbol, \pan);
		Spec.add((name ++ \Amp).asSymbol, \amp);
		Ndef((name ++ "Out").asSymbol).cvcGui(prefix: (name ++ \Out).asSymbol, excemptArgs: [\in]);

		Ndef((name ++ "Out").asSymbol) <<> Ndef((name ++ "Loops").asSymbol);
	}

	initPdef {
		var trace = PatternProxy.new;
		if (this.debug) {
			trace.setSource(
				Pfunc { |e|
					"index: %, bufnum: %, dur: %\n".format(e.channelOffset, e.bufnum, e.dur)
				}.trace
			)
		} {
			trace.setSource(0)
		};

		Pdef((name ++ "Loops").asSymbol,
			Ppar({ |i|
				Pbind(
					\instrument, \grain,
					// buffers in the buffers array may start with a bufnum higher than 0
					// so we check the bufnum by addressing the buffer at index i
					\bufnum, buffers[i].bufnum.postln,
					\start, CVCenter.cvWidgets[(name ++ "Start").asSymbol].split[i],
					\end, CVCenter.cvWidgets[(name ++ "End").asSymbol].split[i],
					\rate, CVCenter.cvWidgets[(name ++ "Rate").asSymbol].split[i],
					\atk, CVCenter.cvWidgets[(name ++ "Atk").asSymbol].split[i],
					\sust, CVCenter.cvWidgets[(name ++ "Sust").asSymbol].split[i],
					\rel, CVCenter.cvWidgets[(name ++ "Rel").asSymbol].split[i],
					\dec, CVCenter.cvWidgets[(name ++ "Dec").asSymbol].split[i],
					\curve, CVCenter.cvWidgets[(name ++ "Curve").asSymbol].split[i],
					\dur, CVCenter.cvWidgets[(name ++ "Dur").asSymbol].split[i]/*.trace(prefix: i.asString ++ ": ")*/,
					\amp, CVCenter.cvWidgets[(name ++ "GrainAmp").asSymbol].split[i],
					\channelOffset, i,
					\trace, trace
				)
			} ! numBuffers)
		);

		Ndef((name ++ "Loops").asSymbol).mold(numBuffers, \audio, \elastic);
		Ndef((name ++ "Loops").asSymbol)[0] = Pdef((name ++ "Loops").asSymbol);
	}

	resetBuffers {
		buffers.do(_.zero);
		[\Start, \End, \Dur].do { |w|
			CVCenter.at((name ++ w).asSymbol).spec.maxval_(bufLength);
			usedBuffers = false ! numBuffers;
		}
	}

	clear { |fadeTime=0.2|
		Ndef((name ++ \Out).asSymbol).clear(fadeTime);
		fork {
			fadeTime.wait;
			Ndef((name ++ \Loops).asSymbol).clear;
			Pdef((name ++ \Loops).asSymbol).clear;
			buffers.do { |b| b.close; b.free };
			recorder.clear;
			all[name] = nil;
		}
	}
}