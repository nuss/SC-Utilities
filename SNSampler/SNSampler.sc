SNSampler : AbstractSNSampler {
	classvar <all;
	var <name, <clock, <>tempo, <beatsPerBar, <numBars, <numBuffers, <server, <inBus;
	var <buffers, <recorder, <metronome, <buffersAllocated = false;
	var recorder, <isPlaying = false;
	var nameString, <>activeBuffers, activeBuffersSeq;
	var <window;

	*new { |name, clock, tempo=1, beatsPerBar=4, numBars=1, numBuffers=5, server, oscFeedbackAddr|
		^super.newCopyArgs(name, clock, tempo, beatsPerBar, numBars, numBuffers, server).init(oscFeedbackAddr);
	}

	init { |oscFeedbackAddr|
		oscFeedbackAddr !? {
			if (oscFeedbackAddr.class !== NetAddr) {
				Error("If supplied oscFeedbackAddr must be a NetAddr. Given: %\n".format(oscFeedbackAddr));
			} {
				this.class.oscFeedbackAddr_(oscFeedbackAddr);
			}
		};
		all ?? { all = () };
		if (name.isNil) {
			name = ("sampler" + (all.size + 1)).asSymbol
		} {
			if (all.includesKey(name.asSymbol)) {
				Error("A sampler under the given name already exists").throw;
			}
		};
		all = all.put(name.asSymbol, this);
		nameString = name.asString;

		server ?? { server = Server.default };
		clock ?? {
			// create a new clock, compensating latency
			clock = TempoClock(this.tempo, 0, server.latency.neg);
		};

		// mark buffers inactive on start
		activeBuffers = 1!numBuffers;
	}

	start {
		if (isPlaying.not) {
			if (server.serverRunning) {
				var bufnums;
				var onOffFunc, bufSetFunc, feedbackFunc;
				var onOff, bufSetter;

				server.bind {
					buffers = Buffer.allocConsecutive(
						numBuffers,
						server,
						(server.sampleRate * beatsPerBar * numBars).asInteger,
						completionMessage: { |buf| "buffer % allocated\n".postf(buf.bufnum) };
					);
					bufnums = buffers.collect(_.bufnum);

					server.sync;
					buffersAllocated = true;

					recorder = NodeProxy.audio(server, 1).clock_(clock).quant_([beatsPerBar, 0, 0, 1]);
					recorder[0] = {
						var soundIn = LeakDC.ar(SoundIn.ar(\in.kr(0), \inputLevel.kr(0.5))).tanh.scope("sampler in");
						BufWr.ar(
							soundIn,
							\bufnum.kr(0),
							// Phasor will ramp from start to end and then jump back to start
							// if this.tempo != 1 we have to move through the buffer at a different rate
							Phasor.ar(0, BufRateScale.kr(\bufnum.kr(0), this.tempo), 0, BufFrames.kr(\bufnum.kr(0)))
						);
						// play silently
						Silent.ar;
					};

					this.schedule;

					CVCenter.cvWidgets[(nameString + "level").asSymbol].oscConnect(
						this.class.oscFeedbackAddr.ip,
						name: '/sampler/amp'
					);
					CVCenter.cvWidgets[(nameString + "tempo").asSymbol].oscConnect(
						this.class.oscFeedbackAddr.ip,
						name: '/sampler/tempo'
					);

					onOffFunc = "{ |cv| if (cv.value == 1) {
						SNSampler.all['%'].resume;
						SNSampler.all['%'].schedule;
					} { SNSampler.all['%'].pause }}".format(name, name, name);
					onOff = this.cvCenterAddWidget(" on/off", 0, #[0, 1, \lin, 1.0], onOffFunc, 0, 0);
					onOff.addAction('sampler toggle feedback', { |cv| SNSampler.oscFeedbackAddr.sendMsg('/sampler/toggle', cv.input) }, active: false);
					onOff.oscConnect(this.class.oscFeedbackAddr.ip, name: '/sampler/toggle', oscMsgIndex: 1);


					bufSetter = this.cvCenterAddWidget(" bufSet", this.activeBuffers, [0!numBuffers, 1!numBuffers, \lin, 1.0], midiMode: 0, softWithin: 0);
					bufnums.do { |bufnum|
						bufSetter.oscConnect(
							this.class.oscFeedbackAddr.ip.postln,
							// nil,
							name: "/buf%/set".format(bufnum),
							slot: bufnum
						)
					};

					// bufSetFunc = "SNSampler.all['%'].activeBuffers_(cv.input);".format(name);
					bufSetFunc = "cv.input.indexOf(1.0) !? {
						SNSampler.all['%'].activeBuffers_(cv.input);
						SNSampler.all['%'].setActiveBuffers(cv.input);
					};".format(name, name);
					bufSetter.addAction('set active buffers', "{ |cv|\n" ++ bufSetFunc ++ "\n}");

					feedbackFunc = "{ |cv|\n";
					activeBuffers.do { |state, bufnum|
						feedbackFunc = feedbackFunc ++
						// "\nSNSampler.oscFeedbackAddr.sendMsg('/buf%/set', SNSampler.all['%'].activeBuffers[%]);".format(bufnum, name, bufnum);
						"\nSNSampler.oscFeedbackAddr.sendMsg('/buf%/set', cv.input[%]);".format(bufnum, bufnum);
					};
					feedbackFunc = feedbackFunc ++ "\n}";
					bufSetter.addAction('feedback', feedbackFunc, active: false);

					this.addMetronome(out: 0, numChannels: 1, amp: 0);

					// recorder.play;
					isPlaying = recorder.isPlaying;
				}
			} {
				"Please boot server '%' before starting the sampler.".format(server).warn;
			}
		} {
			this.resume;
		}
	}

	stop {
		recorder !? {
			recorder.stop;
			isPlaying = recorder.isPlaying;
		}
	}

	pause {
		recorder !? {
			recorder.pause;
		}
	}

	resume {
		recorder !? {
			// make sure sampler and metronome are in sync
			this.schedule;
			recorder.resume;
		}
	}

	// schedule sampling, post current off beat if post == true
	schedule { |post=false|
		var bufnums;

		if (buffers.isNil) {
			"Can't schedule as no buffers have been allocated yet".warn;
		} {
			bufnums = buffers.collect(_.bufnum);
		};

		activeBuffersSeq ?? {
			var initiallyActive = 1!numBuffers;
			this.setActiveBuffers(initiallyActive);
			bufnums.do{ |bn|
				this.class.oscFeedbackAddr.sendMsg("/buf"++bn++"/set", 1);
			}
		};

		recorder !? {
			if (post) {
				recorder[1] = \set -> Pbind(
					\dur, beatsPerBar * numBars,
					\in, CVCenter.use(
						(nameString + "in").asSymbol,
						ControlSpec(0, server.options.firstPrivateBus-1, \lin, 1.0),
						tab: name
					),
					\inputLevel, CVCenter.use(
						(nameString + "level").asSymbol,
						\amp,
						0.5,
						name
					),
					\bufnum, activeBuffersSeq,
					\tempo, CVCenter.use(
						nameString + "tempo",
						#[1, 4, \lin],
						this.tempo,
						name
					),
					\feedback, Pfunc { |ev|
						buffers.do { |b, i|
							if (b.bufnum == ev.bufnum) {
								this.class.oscFeedbackAddr.sendMsg("/buf"++ev.bufnum++"/recording", 1);
							} {
								this.class.oscFeedbackAddr.sendMsg("/buf"++b.bufnum++"/recording", 0);
							}
						}
					},
					\trace, Pfunc { |e| "beatsPerBar:" + e.dur ++ "," + nameString + "beat:" + clock.beatInBar ++ ", buffer:" + e.bufnum }.trace
				);
			} {
				recorder[1] = \set -> Pbind(
					\dur, beatsPerBar * numBars,
					\in, CVCenter.use(
						(nameString + "in").asSymbol,
						ControlSpec(0, server.options.firstPrivateBus-1, \lin, 1.0),
						tab: name
					),
					\inputLevel, CVCenter.use(
						(nameString + "level").asSymbol,
						\amp,
						0.5,
						name
					),
					\bufnum, activeBuffersSeq.trace,
					\tempo, CVCenter.use(
						nameString + "tempo",
						#[1, 4, \lin],
						this.tempo,
						name
					),
					\feedback, Pfunc { |ev|
						buffers.do { |b, i|
							if (b.bufnum == ev.bufnum) {
								this.class.oscFeedbackAddr.sendMsg("/buf"++ev.bufnum++"/recording", 1);
							} {
								this.class.oscFeedbackAddr.sendMsg("/buf"++b.bufnum++"/recording", 0);
							}
						}
					}
				);
			};
			CVCenter.addActionAt(
				(nameString + "tempo").asSymbol,
				'set tempo',
				"{ |cv| SNSampler.all['" ++ nameString ++ "'].tempo = cv.value }"
			)
		}
	}

	allocateBuffers { |numBuffers|
		// pause sampler first
		if (CVCenter.at((nameString + "on/off").asSymbol).notNil) {
			CVCenter.at((nameString + "on/off").asSymbol).input_(0)
		} {
			Error("The sampler could not be paused. Did you accidently remove the widget '" ++ nameString + "on/off'?").throw;
		};
		buffers.do{ |buf| buf.close({ |b| b.freeMsg })};
		buffers = nil;
		buffersAllocated = false;
		// allocation is asynchronous
		server.bind {
			buffers = Buffer.allocConsecutive(
				numBuffers,
				server,
				(server.sampleRate * beatsPerBar * numBars).asInteger
			);
			server.sync;

			buffersAllocated = true;
			// resume sampler
			CVCenter.at((nameString + "on/off").asSymbol).input_(1);
		}
	}

	zero { |bufnum|
		var buffer;

		if (bufnum.isNil) {
			buffers.do(_.zero);
		} {
			buffer = buffers.detect{ |b| b.bufnum == bufnum };
			if (buffer.notNil) {
				buffer.zero({ "buffer % zeroed\n".postf(bufnum) });
			} {
				"buffer at bufnum % does not exist!\n".format(bufnum).warn;
			}
		}
	}

	clear {
		recorder.clear;
		metronome !? {
			Pdef(metronome.name.asSymbol).clear;
			metronome.clear;
		};
		buffers.do({ |b| b.close(_.free) });
		buffersAllocated = false;
		CVCenter.removeAtTab(name);
		all[all.findKeyForValue(this)] = nil;
	}

	server_ { |server|
		// TODO
	}

	// GUI
	front { |parent, rect|
		var rectProps = rect !? {
			[rect.left, rect.top, rect.width, rect.height]
		};

		if (window.isNil or:{ window.isClosed }) {
			SNSamplerGui(this, parent, *rectProps);
			window = parent;
		} { window.front };
	}

	// an acoustic metronome
	addMetronome { |out, numChannels, amp|
		metronome ?? {
			metronome = SNMetronome(
				this, clock, this.tempo, beatsPerBar, server,
				out, numChannels, amp
			)
		}
	}

	removeMetronome {
		metronome.clear;
		metronome = nil;
	}

	// set recording buffers and update recording sequence accordingly
	setActiveBuffers { |input|
		var bufnums = buffers.collect(_.bufnum);

		activeBuffersSeq ?? {
			activeBuffersSeq = PatternProxy.new;
		};
		activeBuffersSeq.source = Pseq(bufnums[input.selectIndices{|it, i| it > 0}], inf);
	}

	// run unit tests
	*test {
		// var controller, model;
		// controller = TestSNSampler.testController;
		// controller.put(\sync, { |changer|
		// 	switch (changer.value,
		// 		\init, { TestSNSampler.runTest("TestSNSampler:test_init") },
		// 		\start, { TestSNSampler.runTest("TestSNSampler:test_start") }
		// 	)
		// });
		// TestSNSampler.testModel.value_(\init).changed(\sync);
		TestSNSampler.runTest("TestSNSampler:test_init");
		TestSNSampler.runTest("TestSNSampler:test_start");
	}
}