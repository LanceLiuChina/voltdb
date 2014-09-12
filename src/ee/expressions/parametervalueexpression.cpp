/* This file is part of VoltDB.
 * Copyright (C) 2008-2014 VoltDB Inc.
 *
 * This file contains original code and/or modifications of original code.
 * Any modifications made by VoltDB Inc. are licensed under the following
 * terms and conditions:
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with VoltDB.  If not, see <http://www.gnu.org/licenses/>.
 */
/* Copyright (C) 2008 by H-Store Project
 * Brown University
 * Massachusetts Institute of Technology
 * Yale University
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

#include "parametervalueexpression.h"

#include "common/debuglog.h"
#include "common/valuevector.h"
#include "common/executorcontext.hpp"
#include "execution/VoltDBEngine.h"

#include <sstream>

namespace voltdb {

    ParameterValueExpression::ParameterValueExpression(int value_idx)
        : AbstractExpression(EXPRESSION_TYPE_VALUE_PARAMETER),
        m_valueIdx(value_idx), m_paramValue()
    {
        VOLT_TRACE("ParameterValueExpression %d", value_idx);
        ExecutorContext* context = ExecutorContext::getExecutorContext();
        VoltDBEngine* engine = context->getEngine();
        assert(engine != NULL);
        NValueArray& params = engine->getParameterContainer();
        assert(value_idx < params.size());
        m_paramValue = &params[value_idx];
    }

    ParameterValueExpression::~ParameterValueExpression() {
    }

    llvm::Value* ParameterValueExpression::codegen(CodegenContext& ctx,
                                                   const TupleSchema*) const {
        llvm::Constant* nvalueAddrAsInt = llvm::ConstantInt::get(ctx.getIntPtrType(),
                                                                 (uintptr_t)m_paramValue);

        // cast the pointer to the nvalue as a pointer to the value.
        // Since the first member of NValue is the 16-byte m_data
        // array, this is okay for all the numeric types.  But if
        // NValue ever changes, this code will break.
        llvm::PointerType* ptrTy = llvm::PointerType::getUnqual(ctx.getLlvmType(m_valueType));
        llvm::Value* castedAddr = ctx.builder().CreateIntToPtr(nvalueAddrAsInt, ptrTy);

        std::ostringstream varName;
        varName << "param_" << m_valueIdx;
        return ctx.builder().CreateLoad(castedAddr, varName.str().c_str());

    }

}

