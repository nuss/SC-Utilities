SNLooper : AbstractSNSampler {
	classvar <all;
	var <name, <clock, <numBuffers, <bufLength, <numChannels, <>randomBufferSelect, <server, <useFFT;
	var <buffers, <recorder, <loopLengths;
	var <sample = false, samplingController, samplingModel, onTime, offTime;
	var usedBuffers, <fftBuffers, fftRecorder;
	var <>debug = false;

	*new { |name, clock, numBuffers, bufLength, numChannels, randomBufferSelect, server, useFFT = false, oscFeedbackAddr, volumeControlNode = 1000, localBufSize = 1024, hopSize = 0.25|
		^super.newCopyArgs(
			name,
			clock ? TempoClock.default,
			numBuffers ? 5, bufLength ? 5,
			numChannels ? 1,
			randomBufferSelect ? false,
			server ? Server.default,
			useFFT
		).init(oscFeedbackAddr, volumeControlNode, localBufSize, hopSize);
	}

	init { |oscFeedbackAddr, volumeControlNode, localBufSize, hopSize|
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
			var in, bufnum;
			buffers = Buffer.allocConsecutive(numBuffers, server, bufLength * server.sampleRate, numChannels);
			loopLengths = bufLength ! numBuffers;
			usedBuffers = false ! numBuffers;
			if (useFFT) {
				fftBuffers = Buffer.allocConsecutive(numBuffers, server, bufLength.calcPVRecSize(localBufSize, hopSize), numChannels)
			};
			server.sync;
			recorder = NodeProxy.audio(server, numChannels).pause;
			if (numChannels < 2) { in = 0 } { in = 0!numChannels };
			recorder[0] = {
				var soundIn = SoundIn.ar(\in.kr(in)).scope("looper in");
				bufnum = \bufnum.kr(buffers[0].bufnum);
				BufWr.ar(
					soundIn,
					bufnum,
					Phasor.ar(\trig.tr(0), BufRateScale.kr(bufnum), 0, BufFrames.kr(bufnum))
				);
				// play silently
				// Silent.ar;
				soundIn * \bypassAmp.kr(1);
			};
			if (useFFT) {
				// fftRecorder = Synth(\pvrec, [\recBufnum, fftBuffers[0].bufnum, \soundBufnum, buffers[0].bufnum, localBufSize, hopSize]);
				recorder[1] = {
					var in, chain, localBuf;
					localBuf = LocalBuf(localBufSize);
					in = PlayBuf.ar(1, bufnum, BufRateScale.kr(bufnum), loop: 0).poll;
					chain = FFT(localBuf, in, hopSize, 1);
					chain = PV_RecordBuf(chain, \recBufnum.kr(fftBuffers[0].bufnum), 0, 1, 0, hopSize, 1);
				}
			};
			CVCenter.scv.loopers ?? {
				CVCenter.scv.loopers = ();
				CVCenter.scv.loopers.put(name, (looper: this, recorder: recorder));
			};
			this.createWidgets;
			server.sync;
			this.prInitSampler;
			this.prSetUpLoops(volumeControlNode);
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
				if (useFFT) {
					recorder.set(\recBufNum, fftBuffers[nextBuf].bufnum);
				};
				CVCenter.at((name ++ "-set bufnum").asSymbol).value_(nextBuf);
				// "buffers[%].bufnum: %\n".postf(nextBuf, buffers[nextBuf].bufnum);
				onTime = nil;
				CVCenter.at((name ++ "-start/stop").asSymbol).input_(0);
				"done".postln;
			}
		})
	}

	prSetSpecConstraints { |index, length|
		CVCenter.at((name ++ \Start).asSymbol).spec.maxval[index] = length / bufLength;
		CVCenter.at((name ++ \End).asSymbol).spec.maxval[index] = length / bufLength;
		// "before - index: %, length: %, loopCVs.dur.spec.maxval[index]: %\n".postf(index, length, loopCVs.dur.spec.maxval[index]);
		CVCenter.at((name ++ \Dur).asSymbol).spec.maxval[index] = length;
		// "after - index: %, length: %, loopCVs.dur.spec.maxval[index]: %\n".postf(index, length, loopCVs.dur.spec.maxval[index]);

		if (this.debug) {
			"buffer index: %\ndur maxval: %\n".postf(
				index,
				CVCenter.at((name ++ \Start).asSymbol).spec.maxval,
				CVCenter.at((name ++ \End).asSymbol).spec.maxval,
				CVCenter.at((name ++ \Dur).asSymbol).spec.maxval
			)
		}
	}

	prSetCVValues { |bufIndex|
		var amps, durs, ends;
		amps = CVCenter.at((name ++ \GrainAmp).asSymbol).value;
		amps[bufIndex] = 1;
		CVCenter.at((name ++ \GrainAmp).asSymbol).value_(amps);
		durs = CVCenter.at((name ++ \Dur).asSymbol).value;
		durs[bufIndex] = loopLengths[bufIndex];
		CVCenter.at((name ++ \Dur).asSymbol).value_(durs);
		CVCenter.at((name ++ \End).asSymbol).input_(1);

		if (this.debug) {
			"buffer index: %\ndurs value: %\nends value: %\n".postf(
				bufIndex,
				CVCenter.at((name ++ \Dur).asSymbol).value,
				CVCenter.at((name ++ \End).asSymbol).value
			)
		}
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

	prSetUpLoops { |volumeControl|
		CVCenter.use((name ++ "Atk").asSymbol, #[0.02, 3, \exp] ! numBuffers, tab: (name ++ "Loops").asSymbol);
		CVCenter.use((name ++ "Sust").asSymbol, #[0.1, 1.0] ! numBuffers, 1, (name ++ "Loops").asSymbol);
		CVCenter.use((name ++ "Rel").asSymbol, #[0.02, 3, \exp] ! numBuffers, tab: (name ++ "Loops").asSymbol);
		CVCenter.use((name ++ "Curve").asSymbol, #[-4, 4] ! numBuffers, 0, (name ++ "Loops").asSymbol);
		CVCenter.use((name ++ "Dur").asSymbol, [0.1!numBuffers, loopLengths], bufLength, (name ++ "Loops").asSymbol);
		CVCenter.use((name ++ "GrainAmp").asSymbol, \amp ! numBuffers, tab: (name ++ "Loops").asSymbol);
		if (useFFT.not) {
			CVCenter.use((name ++ "Start").asSymbol, [0!numBuffers, loopLengths/bufLength], tab: (name ++ "Loops").asSymbol);
			CVCenter.use((name ++ "End").asSymbol, [0!numBuffers, loopLengths/bufLength], loopLengths/bufLength, (name ++ "Loops").asSymbol);
			CVCenter.use((name ++ "Rate").asSymbol, #[-2, 2] ! numBuffers, 1.0, tab: (name ++ "Loops").asSymbol);
		} {
			CVCenter.use((name ++ \Cursor).asSymbol, [0!numBuffers, loopLengths/bufLength], tab: (name ++ \Loops).asSymbol);
		};

		this.initPdef(useFFT);

		Ndef((name ++ "Out").asSymbol)[0] = {
			Splay.ar(\in.ar(0 ! numBuffers), (name ++ \Spread).asSymbol.kr(0.5), 1, (name ++ \Center).asSymbol.kr(0.0))
		};

		Spec.add((name ++ \Center).asSymbol, \pan);
		Spec.add((name ++ \Amp).asSymbol, \amp);
		Ndef((name ++ "Out").asSymbol).cvcGui(prefix: (name ++ \Out).asSymbol, excemptArgs: [\in]);

		Ndef((name ++ "Out").asSymbol) <<> Ndef((name ++ "Loops").asSymbol);
		Ndef((name ++ "Out").asSymbol)[volumeControl] = \filter -> { |in|
			in * (name ++ \Amp).asSymbol.kr(1)
		}
	}

	initPdef {
		var pbinds, trace = PatternProxy.new;
		if (this.debug) {
			trace.setSource(
				Pfunc { |e|
					"index: %, bufnum: %, dur: %\n".format(e.channelOffset, e.bufnum, e.dur)
				}.trace
			)
		} {
			trace.setSource(0)
		};

		CVCenter.at((name ++ \Dur).asSymbol).spec_([0.1!numBuffers, loopLengths].asSpec);
		if (useFFT) {
			CVCenter.at((name ++ \Cursor).asSymbol).spec_([0!numBuffers, loopLengths/bufLength].asSpec);
		} {
			CVCenter.at((name ++ \Start).asSymbol).spec_([0!numBuffers, loopLengths/bufLength].asSpec);
			CVCenter.at((name ++ \End).asSymbol).spec_([0!numBuffers, loopLengths/bufLength, \lin, 0.0, loopLengths/bufLength].asSpec);
		};

		if (useFFT) {
			pbinds = { |i|
				Pbind(
					\instrument, \pvgrain,
					\recBufnum, fftBuffers[i].bufnum,
					\cursor, CVCenter.cvWidgets[(name ++ \Cursor).asSymbol].split[i],
					\atk, CVCenter.cvWidgets[(name ++ "Atk").asSymbol].split[i],
					\sust, CVCenter.cvWidgets[(name ++ "Sust").asSymbol].split[i],
					\rel, CVCenter.cvWidgets[(name ++ "Rel").asSymbol].split[i],
					\curve, CVCenter.cvWidgets[(name ++ \Curve).asSymbol].split[i],
					\dur, CVCenter.cvWidgets[(name ++ \Dur).asSymbol].split[i],
					\grainAmp, CVCenter.cvWidgets[(name ++ \GrainAmp).asSymbol].split[i],
					\channelOffset, i,
					\trace, trace
				)
			} ! numBuffers;
		} {
			pbinds = { |i|
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
					\curve, CVCenter.cvWidgets[(name ++ "Curve").asSymbol].split[i],
					\dur, CVCenter.cvWidgets[(name ++ "Dur").asSymbol].split[i]/*.trace(prefix: i.asString ++ ": ")*/,
					\grainAmp, CVCenter.cvWidgets[(name ++ "GrainAmp").asSymbol].split[i],
					\channelOffset, i,
					\trace, trace
				)
			} ! numBuffers
		};

		Pdef((name ++ "Loops").asSymbol,
			Ppar(pbinds)
		);

		Ndef((name ++ "Loops").asSymbol).mold(numBuffers, \audio, \elastic);
		Ndef((name ++ "Loops").asSymbol)[0] = Pdef((name ++ "Loops").asSymbol);
	}

	resetBuffers {
		buffers.do(_.zero);
		if (useFFT) {
			fftBuffers.do(_.zero);
			[\Cursor, \Dur].do { |w|
				CVCenter.at((name ++ w).asSymbol).spec.maxval_(bufLength);
			}
		} {
			[\Start, \End, \Dur].do { |w|
				CVCenter.at((name ++ w).asSymbol).spec.maxval_(bufLength);
			}
		};
		usedBuffers = false ! numBuffers;
	}

	clear { |fadeTime=0.2|
		Ndef((name ++ \Out).asSymbol).clear(fadeTime);
		fork {
			fadeTime.wait;
			Ndef((name ++ \Loops).asSymbol).clear;
			Pdef((name ++ \Loops).asSymbol).clear;
			buffers.do { |b| b.close; b.free };
			if (useFFT) { fftBuffers.do { |fftb| fftb.close; fftb.free }};
			recorder.clear;
			recorder = nil;
			all[name] = nil;
		}
	}
}