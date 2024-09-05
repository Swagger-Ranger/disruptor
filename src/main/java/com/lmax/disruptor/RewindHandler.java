/*
 * Copyright 2023 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lmax.disruptor;

/**
 * RewindHandler 是直接处理 {@link BatchRewindStrategy} 的核心类。它负责在事件处理过程中，根据策略来决定如何处理回滚请求。
 * 核心都在 {@link BatchEventProcessor} 内部实现调用
 */
public interface RewindHandler
{
    long attemptRewindGetNextSequence(RewindableException e, long startOfBatchSequence) throws RewindableException;
}
